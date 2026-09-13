import { Router } from 'express';
import twilio from 'twilio';
import { config, publicWsUrl } from '../config.js';

const { VoiceResponse } = twilio.twiml;

export const voiceRouter = Router();

/**
 * Verifies X-Twilio-Signature so nobody else can trigger media streams
 * against your OpenAI key. Twilio signs the *public* URL, so we tell the
 * validator what URL Twilio thinks it called (behind ngrok/Cloudflare).
 */
function twilioSignatureGuard(req, res, next) {
  if (!config.twilio.validateSignature) return next();
  const signature = req.header('X-Twilio-Signature') ?? '';
  const fullUrl = `${config.publicUrl}${req.originalUrl}`;
  const valid = twilio.validateRequest(config.twilio.authToken, signature, fullUrl, req.body);
  if (!valid) {
    console.warn('[voice] rejected request with bad Twilio signature');
    return res.status(403).send('Forbidden');
  }
  return next();
}

/**
 * POST /voice — Twilio hits this when the forwarded call lands on your number.
 * We answer, play a short greeting, then open a bidirectional Media Stream.
 * <Connect><Stream> keeps the call open for as long as the socket lives.
 */
voiceRouter.post('/voice', twilioSignatureGuard, (req, res) => {
  const { From = 'unknown', CallSid = '', ForwardedFrom = '' } = req.body;
  console.log(`[voice] incoming CallSid=${CallSid} from=${From} forwardedFrom=${ForwardedFrom}`);

  const twiml = new VoiceResponse();
  twiml.say({ voice: 'Polly.Amy' }, config.greetingText);

  const connect = twiml.connect();
  const stream = connect.stream({ url: `${publicWsUrl}/twilio-media` });
  // Custom parameters arrive in the "start" message so the app can match
  // the caller number it just rejected with this stream.
  stream.parameter({ name: 'from', value: From });
  stream.parameter({ name: 'callSid', value: CallSid });

  res.type('text/xml').send(twiml.toString());
});

/** Twilio status callbacks (optional; wire in the console if you want them). */
voiceRouter.post('/voice/status', twilioSignatureGuard, (req, res) => {
  console.log(`[voice] status CallSid=${req.body.CallSid} → ${req.body.CallStatus}`);
  res.sendStatus(204);
});
