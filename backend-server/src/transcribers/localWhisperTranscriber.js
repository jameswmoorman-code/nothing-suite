import { spawn } from 'node:child_process';
import path from 'node:path';
import readline from 'node:readline';
import { fileURLToPath } from 'node:url';
import { config } from '../config.js';

const WORKER = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'whisper_worker.py');

/**
 * Free, on-this-machine transcription. No key, no per-minute bill, audio
 * never leaves the user's own hardware.
 *
 * Pipeline:  μ-law base64 → PCM16 8 kHz → tiny energy-based voice detector
 *            → utterance chunks → python worker (faster-whisper) → text
 *
 * Latency is "a second or two after the caller pauses" rather than
 * word-by-word, which is fine for deciding whether to pick up.
 */
export class LocalWhisperTranscriber {
  #handlers;
  #proc = null;
  #nextId = 1;

  // utterance buffer
  #chunks = [];
  #chunkSamples = 0;
  #speechMs = 0;
  #silenceMs = 0;
  #inSpeech = false;

  ready = false;

  constructor(handlers) {
    this.#handlers = handlers;
  }

  connect() {
    return new Promise((resolve) => {
      this.#proc = spawn(config.local.pythonBin, [WORKER, '--model', config.local.model], {
        stdio: ['pipe', 'pipe', 'inherit'],
      });

      this.#proc.on('error', (e) => {
        this.#handlers.onError(`local transcriber failed to start: ${e.message}`);
        resolve();
      });
      this.#proc.stdin.on('error', (e) => this.#handlers.onError(`local transcriber pipe: ${e.message}`));
      this.#proc.on('exit', (code) => {
        this.ready = false;
        if (code) this.#handlers.onError(`local transcriber exited (${code})`);
      });

      const rl = readline.createInterface({ input: this.#proc.stdout });
      rl.on('line', (line) => {
        let msg;
        try {
          msg = JSON.parse(line);
        } catch {
          return;
        }
        if (msg.event === 'ready') {
          this.ready = true;
          resolve();
        } else if (msg.event === 'result') {
          const text = (msg.text ?? '').trim();
          if (text) this.#handlers.onFinal(text);
        } else if (msg.event === 'error') {
          this.#handlers.onError(msg.message);
        }
      });

      // If the model takes ages to load, don't block the call forever.
      setTimeout(resolve, 15_000).unref();
    });
  }

  appendUlawBase64(base64Payload) {
    if (!this.ready) return;
    const pcm = ulawToPcm16(Buffer.from(base64Payload, 'base64'));
    const ms = (pcm.length / 2 / SAMPLE_RATE) * 1000;
    const loud = rms(pcm) > VAD_THRESHOLD;

    this.#chunks.push(pcm);
    this.#chunkSamples += pcm.length / 2;

    if (loud) {
      this.#inSpeech = true;
      this.#speechMs += ms;
      this.#silenceMs = 0;
    } else {
      this.#silenceMs += ms;
    }

    const utteranceDone = this.#inSpeech && this.#silenceMs >= END_SILENCE_MS && this.#speechMs >= MIN_SPEECH_MS;
    const tooLong = this.#chunkSamples / SAMPLE_RATE >= MAX_UTTERANCE_S;
    const onlyNoise = !this.#inSpeech && this.#chunkSamples / SAMPLE_RATE >= 3;

    if (utteranceDone || tooLong) this.#flush();
    else if (onlyNoise) this.#reset(); // drop leading silence so buffers don't grow
  }

  #flush() {
    const audio = Buffer.concat(this.#chunks);
    this.#reset();
    if (!this.#proc || this.#proc.exitCode !== null) return;
    const header = Buffer.alloc(8);
    header.writeUInt32LE(this.#nextId++, 0);
    header.writeUInt32LE(audio.length, 4);
    this.#proc.stdin.write(Buffer.concat([header, audio]));
  }

  #reset() {
    this.#chunks = [];
    this.#chunkSamples = 0;
    this.#speechMs = 0;
    this.#silenceMs = 0;
    this.#inSpeech = false;
  }

  close() {
    if (this.#inSpeech) this.#flush();
    this.ready = false;
    setTimeout(() => this.#proc?.kill(), 4000).unref(); // let the last chunk finish
  }
}

// ---- audio helpers ---------------------------------------------------------

const SAMPLE_RATE = 8000;
const VAD_THRESHOLD = 700; // RMS on int16 scale; phone lines idle around 100–300
const END_SILENCE_MS = 700;
const MIN_SPEECH_MS = 400;
const MAX_UTTERANCE_S = 10;

const ULAW_TABLE = new Int16Array(256);
for (let i = 0; i < 256; i++) {
  let u = ~i & 0xff;
  const sign = u & 0x80;
  const exponent = (u >> 4) & 0x07;
  const mantissa = u & 0x0f;
  let sample = ((mantissa << 3) + 0x84) << exponent;
  sample -= 0x84;
  ULAW_TABLE[i] = sign ? -sample : sample;
}

function ulawToPcm16(ulaw) {
  const out = Buffer.alloc(ulaw.length * 2);
  for (let i = 0; i < ulaw.length; i++) out.writeInt16LE(ULAW_TABLE[ulaw[i]], i * 2);
  return out;
}

function rms(pcm) {
  let sum = 0;
  const n = pcm.length / 2;
  for (let i = 0; i < pcm.length; i += 2) {
    const s = pcm.readInt16LE(i);
    sum += s * s;
  }
  return Math.sqrt(sum / Math.max(n, 1));
}
