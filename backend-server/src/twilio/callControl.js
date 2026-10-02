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
  hold:       { say: 'Please hold for a moment.', hold: true },      // music to the caller until resumed
  resume:     { say: 'Thanks for holding.', resume: true },
});

/** Twilio's royalty-free hold music (looped). Swap for your own MP3 URL if you like. */
export const HOLD_MUSIC_URL = 'http://com.twilio.music.classical.s3.amazonaws.com/BusyStrings.mp3';

/** Live calls by CallSid, so stop/start churn from updates doesn't look like a hang-up. */
class CallRegistry {
  #calls = new Map();
  /** Called when nobody reconnects after an action: ends the call for the app. */
  onOrphaned = null;
  start(callSid, from) {
    const existing = this.#calls.get(callSid);
    if (existing) {
      existing.reconnecting = false;
      clearTimeout(existing.reconnectTimer); existing.reconnectTimer = null;
      return { resumed: true, from: existing.from };
    }
    this.#calls.set(callSid, { from, reconnecting: false, endReason: null, reconnectTimer: null, said: false, held: false });
    return { resumed: false, from };
  }
  /** The caller has said something we transcribed. */
  markSaid(callSid) { const c = this.#calls.get(callSid); if (c) c.said = true; }
  /** How the call ended, in words the user will read. */
  #endText(c, fallback) {
    if (c.endReason) return c.endReason;
    if (/hung up|socket closed/i.test(fallback) && !c.said) return 'hung up without leaving a message';
    return fallback;
  }
  /** Caller parked on hold music: no stream for a while, and that's fine. */
  hold(callSid, held) {
    const c = this.#calls.get(callSid);
    if (!c) return;
    c.held = held;
    if (held) { c.reconnecting = true; clearTimeout(c.reconnectTimer); c.reconnectTimer = null; }
  }
  isHeld(callSid) { return this.#calls.get(callSid)?.held ?? false; }
  expectReconnect(callSid) {
    const c = this.#calls.get(callSid);
    if (!c) return;
    c.reconnecting = true;
    if (c.held) return;                         // the watchdog still catches a hang-up
    // If the caller hangs up while the concierge is mid-sentence, Twilio never opens
    // the next stream and no one would report the end. Give it 25 s, then call it.
    clearTimeout(c.reconnectTimer);
    c.reconnectTimer = setTimeout(() => {
      if (!this.#calls.has(callSid) || !c.reconnecting) return;
      this.#calls.delete(callSid);
      this.onOrphaned?.(callSid, c.from, this.#endText(c, 'caller hung up'));
    }, 25_000);
  }
  /** Twilio status callback says the call is over: end it whatever state we're in. */
  forceEnd(callSid, reason) {
    const c = this.#calls.get(callSid);
    if (!c) return null;
    clearTimeout(c.reconnectTimer);
    this.#calls.delete(callSid);
    return { from: c.from, reason: this.#endText(c, reason) };
  }
  willEnd(callSid, reason) { const c = this.#calls.get(callSid); if (c) c.endReason = reason; }
  /** Returns null when the stop should be ignored (a reconnect is coming), else the end reason. */
  stop(callSid, fallbackReason) {
    const c = this.#calls.get(callSid);
    if (!c) return fallbackReason;
    if (c.reconnecting) return null;
    this.#calls.delete(callSid);
    return this.#endText(c, fallbackReason);
  }
  from(callSid) { return this.#calls.get(callSid)?.from; }
  /** CallSids we believe are still live. */
  active() { return [...this.#calls.keys()]; }
}
export const registry = new CallRegistry();

const ENDED = new Set(['completed', 'busy', 'failed', 'no-answer', 'canceled']);

/**
 * Watchdog: Twilio only tells us about a call through the media stream, and
 * there is no stream while the greeting plays or while the concierge is
 * speaking. If the caller hangs up then, nobody would report it — so while
 * any call is open we ask Twilio every few seconds whether it still is.
 */
export function startCallWatchdog(broadcaster, intervalMs = 4000) {
  setInterval(async () => {
    for (const callSid of registry.active()) {
      try {
        const call = await client.calls(callSid).fetch();
        if (!ENDED.has(call.status)) continue;
        const ended = registry.forceEnd(callSid, call.status === 'completed' ? 'caller hung up' : `call ${call.status}`);
        if (!ended) continue;
        console.log(`[watchdog] ${callSid} is ${call.status} → ending on the phone`);
        broadcaster.send({ type: 'call_ended', callSid, from: ended.from, text: ended.reason });
      } catch (e) {
        console.warn(`[watchdog] could not check ${callSid}: ${e.message}`);
      }
    }
  }, intervalMs).unref();
}

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

  if (spec.hold) {
    registry.hold(callSid, true);
    twiml.play({ loop: 0 }, HOLD_MUSIC_URL);
  } else if (spec.dial) {
    const target = (to ?? config.userNumber ?? '').trim();
    if (!target) throw new Error('No number to connect to: set MY NUMBER in the app or USER_NUMBER in .env');
    registry.hold(callSid, false);
    registry.willEnd(callSid, 'connected to you');
    twiml.dial({ callerId: config.twilioNumber || undefined, answerOnBridge: true }, target);
  } else if (spec.hangup) {
    registry.willEnd(callSid, action === 'callback' ? 'ended: you will call back' : 'ended by you');
    twiml.hangup();
  } else {
    registry.hold(callSid, false);
    registry.expectReconnect(callSid);
    streamTwiml(twiml, from, callSid);
  }

  console.log(`[action] ${action} on ${callSid}: "${line}"`);
  try {
    await client.calls(callSid).update({ twiml: twiml.toString() });
  } catch (e) {
    if (/not in-progress/i.test(e.message)) {
      const ended = registry.forceEnd(callSid, 'caller hung up');
      broadcaster.send({ type: 'call_ended', callSid, from, text: ended?.reason ?? 'caller hung up' });
      throw new Error('The caller has already hung up.');
    }
    throw e;
  }
  broadcaster.send({ type: 'assistant', callSid, from, text: line });
  if (spec.hold || action === 'resume') broadcaster.send({ type: 'hold_state', callSid, from, held: !!spec.hold });
  return line;
}
