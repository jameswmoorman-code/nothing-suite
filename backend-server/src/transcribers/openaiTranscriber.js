import WebSocket from 'ws';
import { config } from '../config.js';

const REALTIME_URL = 'wss://api.openai.com/v1/realtime?intent=transcription';

/**
 * Streams μ-law audio to an OpenAI Realtime *transcription* session and
 * surfaces text as it arrives. Nothing is persisted; audio never touches disk.
 *
 * Events we care about from OpenAI:
 *   conversation.item.input_audio_transcription.delta      → partial text
 *   conversation.item.input_audio_transcription.completed  → finished utterance
 */
export class OpenAITranscriber {
  #ws = null;
  #handlers;
  ready = false;

  constructor({ onDelta, onFinal, onError }) {
    this.#handlers = { onDelta, onFinal, onError };
  }

  connect() {
    return new Promise((resolve) => {
      this.#ws = new WebSocket(REALTIME_URL, {
        headers: {
          Authorization: `Bearer ${config.openai.apiKey}`,
          'OpenAI-Beta': 'realtime=v1',
        },
      });

      this.#ws.on('open', () => {
        this.#ws.send(
          JSON.stringify({
            type: 'transcription_session.update',
            session: {
              input_audio_format: 'g711_ulaw',
              input_audio_transcription: {
                model: config.openai.transcribeModel,
                language: 'en',
              },
              turn_detection: {
                type: 'server_vad',
                threshold: 0.5,
                prefix_padding_ms: 300,
                silence_duration_ms: 600,
              },
              input_audio_noise_reduction: { type: 'far_field' },
            },
          }),
        );
        this.ready = true;
        resolve();
      });

      this.#ws.on('message', (raw) => {
        let evt;
        try {
          evt = JSON.parse(raw.toString());
        } catch {
          return;
        }
        switch (evt.type) {
          case 'conversation.item.input_audio_transcription.delta':
            if (evt.delta) this.#handlers.onDelta(evt.delta);
            break;
          case 'conversation.item.input_audio_transcription.completed':
            if (evt.transcript) this.#handlers.onFinal(evt.transcript);
            break;
          case 'error':
            console.error('[openai] error', evt.error);
            this.#handlers.onError(evt.error?.message ?? 'OpenAI error');
            break;
          default:
            break;
        }
      });

      this.#ws.on('error', (e) => {
        console.error('[openai] socket error', e.message);
        this.#handlers.onError(e.message);
        resolve(); // don't hang the caller; ready stays false
      });

      this.#ws.on('close', () => {
        this.ready = false;
      });
    });
  }

  appendUlawBase64(base64Payload) {
    if (!this.ready || this.#ws.readyState !== WebSocket.OPEN) return;
    this.#ws.send(JSON.stringify({ type: 'input_audio_buffer.append', audio: base64Payload }));
  }

  close() {
    this.ready = false;
    try {
      this.#ws?.close();
    } catch {
      /* ignore */
    }
  }
}
