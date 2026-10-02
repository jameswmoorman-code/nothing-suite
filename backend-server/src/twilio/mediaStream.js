import { createTranscriber } from '../transcribers/index.js';
import { registry } from './callControl.js';
import { config } from '../config.js';
import { assessUtterance, forgetCall } from '../screening/scamDetector.js';
import { onHoldSpeech } from '../hold/holdForMe.js';
import { prefs } from '../prefs.js';

/**
 * One Twilio Media Stream == one live screened call.
 *
 * Twilio sends JSON frames: connected → start → media* → (mark) → stop.
 * `media.payload` is base64 μ-law @ 8 kHz mono — exactly the `g711_ulaw`
 * format OpenAI's Realtime API accepts, so we forward it untouched.
 */
export function handleTwilioMediaSocket(ws, broadcaster) {
  let streamSid = null;
  let callSid = null;
  let from = 'unknown';
  let transcriber = null;
  let mediaFrames = 0;
  let holdSession = null;     // set when this stream is Hold For Me listening to the company

  ws.on('message', async (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return;
    }

    switch (msg.event) {
      case 'connected':
        break;

      case 'start': {
        streamSid = msg.start.streamSid;
        callSid = msg.start.callSid;
        from = msg.start.customParameters?.from ?? 'unknown';
        holdSession = msg.start.customParameters?.mode === 'hold' ? msg.start.customParameters?.session : null;
        console.log(`[media] start stream=${streamSid} call=${callSid} ${holdSession ? `hold=${holdSession}` : `from=${from}`}`);

        if (holdSession) {
          // Hold For Me: we're the listener on the company leg. No screening, no app transcript.
          transcriber = createTranscriber({
            onDelta: () => {},
            onFinal: (text) => onHoldSpeech(holdSession, text).catch((e) => console.warn(`[hold] ${e.message}`)),
            onError: (err) => console.warn(`[transcriber] ${err}`),
          });
          await transcriber.connect();
          break;
        }

        transcriber = createTranscriber({
          onDelta: (text) => broadcaster.send({ type: 'delta', callSid, from, text }),
          onFinal: (text) => {
            console.log(`[transcript] ${from}: ${text}`);
            registry.markSaid(callSid);
            broadcaster.send({ type: 'final', callSid, from, text });
            const alert = assessUtterance(callSid, text);        // scam shield
            if (alert) broadcaster.send({ ...alert, from });
          },
          onError: (err) => { console.warn(`[transcriber] ${err}`); broadcaster.send({ type: 'error', callSid, from, text: String(err) }); },
        });
        await transcriber.connect();
        const { resumed } = registry.start(callSid, from);
        if (!resumed) {
          broadcaster.send({ type: 'call_started', callSid, from });
          broadcaster.send({ type: 'assistant', callSid, from, text: prefs.greeting });
        }
        break;
      }

      case 'media':
        if (transcriber) {
          transcriber.appendUlawBase64(msg.media.payload);
          mediaFrames += 1;
        }
        break;

      case 'stop':
        console.log(`[media] stop stream=${streamSid} (${mediaFrames} frames)`);
        cleanup('caller hung up');
        break;

      default:
        break;
    }
  });

  ws.on('close', () => cleanup('socket closed'));
  ws.on('error', (e) => cleanup(`socket error: ${e.message}`));

  let done = false;
  function cleanup(reason) {
    if (done) return;
    done = true;
    try { transcriber?.close(); } catch (e) { console.warn(`[transcriber] close failed: ${e.message}`); }
    if (holdSession) return;                        // hold sessions end via their own status callbacks
    const finalReason = registry.stop(callSid, reason);
    if (finalReason === null) return;               // the concierge is mid-sentence; stream reconnects shortly
    forgetCall(callSid);
    broadcaster.send({ type: 'call_ended', callSid, from, text: finalReason });
  }
}
