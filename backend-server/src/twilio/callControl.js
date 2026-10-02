import twilio from 'twilio';
import { config, publicWsUrl } from '../config.js';

const { VoiceResponse } = twilio.twiml;

/**
 * What the phone can make the concierge do mid-call.
 *
 * Twilio's <Connect><Stream> blocks the call, so to make the concierge speak
 * we REST-update the live call with fresh TwiML: <Say> the line, then reopen
 * the stream so transcription carries on. The brief stop/start of the stream
 * is hidden from the app by the registry (see `expectReconnect`).
 */
export const ACTIONS = Object.freeze({
  ask_reason: { say: "Can you tell me what it's regarding?", resume: true },
  callback:   { say: "They can't take the call right now, but they'll call you back. Goodbye.", hangup: true },
  message:    { say: "They can't take the call right now. Please leave a message, then hang up when you're done.", resume: true },
  hangup:     { say: "Goodbye.", hangup: true },
  connect:    { say: 'Connecting you now.', dial: true },
  say:        { resume: true },                       // free text from the app
});

/** Live calls by CallSid, so stop/start churn from updates doesn't look like a hang-up. */
class CallRegistry {
  #calls = new Map();
  start(callSid, from) {
    const existing = this.#calls.get(callSid);
    if (existing) { existing.reconnecting = false; return { resumed: true, from: existing.from }; }
    this.#calls.set(callSid, { from, reconnecting: false, endReason: null });
    return { resumed: false, from };
  }
  expectReconnect(callSid) { const c = this.#calls.get(callSid); if (c) c.reconnecting = true; }
  willEnd(callSid, reason) { const c = this.#calls.get(callSid); if (c) c.endReason = reason; }
  /** Returns null when the stop should be ignored (a reconnect is coming), else the end reason. */
  stop(callSid, fallbackReason) {
    const c = this.#calls.get(callSid);
    if (!c) return fallbackReason;
    if (c.reconnecting) return null;
    this.#calls.delete(callSid);
    return c.endReason ?? fallbackReason;
  }
  from(callSid) { return this.#calls.get(callSid)?.from; }
}
export const registry = new CallRegistry();

const client = twilio(config.twilio.accountSid, config.twilio.authToken);

function streamTwiml(twiml, from, callSid) {
  const stream = twiml.connect().stream({ url: `${publicWsUrl}/twilio-media` });
  stream.parameter({ name: 'from', value: from });
  stream.parameter({ name: 'callSid', value: callSid });
}

/**
 * Apply an action to a live call. Returns the line the concierge will say
 * (so the app can show it), or throws with a human-readable reason.
 */
export async function applyAction({ callSid, action, text, to }, broadcaster) {
  const spec = ACTIONS[action];
  if (!spec) throw new Error(`unknown action "${action}"`);
  const from = registry.from(callSid) ?? 'unknown';
  const line = action === 'say' ? String(text ?? '').trim() : spec.say;
  if (!line) throw new Error('nothing to say');

  const twiml = new VoiceResponse();
  twiml.say({ voice: 'Polly.Amy' }, line);

  if (spec.dial) {
    const target = (to ?? config.userNumber ?? '').trim();
    if (!target) throw new Error('No number to connect to: set MY NUMBER in the app or USER_NUMBER in .env');
    registry.willEnd(callSid, 'connected to you');
    twiml.dial({ callerId: config.twilioNumber || undefined, answerOnBridge: true }, target);
  } else if (spec.hangup) {
    registry.willEnd(callSid, action === 'callback' ? 'ended: you will call back' : 'ended by you');
    twiml.hangup();
  } else {
    registry.expectReconnect(callSid);
    streamTwiml(twiml, from, callSid);
  }

  console.log(`[action] ${action} on ${callSid}: "${line}"`);
  broadcaster.send({ type: 'assistant', callSid, from, text: line });
  await client.calls(callSid).update({ twiml: twiml.toString() });
  return line;
}
