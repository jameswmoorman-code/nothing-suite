import { createTranscriber } from '../transcribers/index.js';

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
        console.log(`[media] start stream=${streamSid} call=${callSid} from=${from}`);

        transcriber = createTranscriber({
          onDelta: (text) => broadcaster.send({ type: 'delta', callSid, from, text }),
          onFinal: (text) => broadcaster.send({ type: 'final', callSid, from, text }),
          onError: (err) => broadcaster.send({ type: 'error', callSid, from, text: String(err) }),
        });
        await transcriber.connect();
        broadcaster.send({ type: 'call_started', callSid, from });
        break;
      }

      case 'media':
        if (transcriber?.ready) {
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
    transcriber?.close();
    broadcaster.send({ type: 'call_ended', callSid, from, text: reason });
  }
}
