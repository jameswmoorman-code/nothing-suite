import crypto from 'node:crypto';
import twilio from 'twilio';
import { config, publicWsUrl } from '../config.js';
import { say } from '../prefs.js';

const { VoiceResponse } = twilio.twiml;
const client = twilio(config.twilio.accountSid, config.twilio.authToken);

/**
 * Hold For Me.
 *
 *   1. start(to)      Twilio rings YOU; when you answer it dials the company and bridges you.
 *   2. holdNow()      you tap HOLD FOR ME: your leg hangs up with a "I'll ring you back",
 *                     the company leg moves into a conference with a media stream so the
 *                     concierge can listen.
 *   3. onSpeech()     hold music and "your call is important to us" loops repeat; a human
 *                     doesn't. First non-repeating human-sounding line → HUMAN.
 *   4. human          concierge tells the agent "please hold, connecting you now", rings you,
 *                     and drops you into the same conference. You're through.
 *
 * States: dialing_you → connecting → talking → holding → human → joined → ended
 */
const sessions = new Map();          // id → session
const byCall = new Map();            // any CallSid → session id

const RECORDED = /\b(your call is important|please (continue to )?hold|currently experiencing|high(er than normal)? (call )?volume|in a queue|position in the queue|next available|all (of )?our (agents|advisors|operators|team)|press (one|two|three|\d)|calls? (may|will) be recorded|for (training|quality)|visit our website|www\.|thank you for (waiting|holding|your patience)[.,!]?$)\b/i;
const HUMAN = /\b(hello|hi there|hiya|good (morning|afternoon|evening)|speaking|how can i help|how may i help|you'?re (through|speaking) to|my name'?s|my name is|this is \w+ (speaking|here)|can i take your|who am i speaking|what can i do for you|sorry (to keep you|for the wait)|thanks? for (holding|waiting|your patience),? (how|what|can|my|you)\b)\b/i;

function norm(t) { return t.toLowerCase().replace(/[^a-z0-9 ]/g, '').replace(/\s+/g, ' ').trim(); }
function key(t) { return norm(t).split(' ').slice(0, 7).join(' '); }

class HoldSession {
  constructor(to) {
    this.id = crypto.randomBytes(4).toString('hex');
    this.to = to;
    this.state = 'dialing_you';
    this.userSid = null;       // the leg to your mobile (first call)
    this.companySid = null;    // the leg to the company
    this.ringBackSid = null;   // the second call to your mobile
    this.heard = [];           // what the concierge heard while holding
    this.seen = new Set();     // repeated announcements
    this.holdStartedAt = null;
    this.timer = null;
  }
  get conference() { return `hold-${this.id}`; }
}

let broadcaster = null;
export function attachHoldBroadcaster(b) { broadcaster = b; }

function tell(s, extra = {}) {
  console.log(`[hold] ${s.id} → ${s.state}${extra.text ? `: ${extra.text}` : ''}`);
  broadcaster?.send({ type: 'hold', session: s.id, state: s.state, to: s.to, ...extra });
}

const streamTwiml = (vr, s) => {
  const start = vr.start();
  const st = start.stream({ url: `${publicWsUrl}/twilio-media` });
  st.parameter({ name: 'mode', value: 'hold' });
  st.parameter({ name: 'session', value: s.id });
};
const conferenceTwiml = (vr, s, { endOnExit }) => {
  const d = vr.dial();
  d.conference({ beep: false, startConferenceOnEnter: true, endConferenceOnExit: endOnExit, waitUrl: '' }, s.conference);
};

// ---- public API -----------------------------------------------------------------

export async function startHold(to) {
  const user = (config.userNumber ?? '').trim();
  if (!user) throw new Error('Set USER_NUMBER in .env (or MY NUMBER in the app) first.');
  if (!/^\+?[0-9 ]{6,}$/.test(to)) throw new Error('That does not look like a phone number.');
  const s = new HoldSession(to.replace(/\s+/g, ''));
  sessions.set(s.id, s);
  const call = await client.calls.create({
    to: user,
    from: config.twilioNumber,
    url: `${config.publicUrl}/hold/you-answered?session=${s.id}`,
    statusCallback: `${config.publicUrl}/hold/status?session=${s.id}&leg=user`,
    statusCallbackEvent: ['completed'],
    timeout: 25,
  });
  s.userSid = call.sid; byCall.set(call.sid, s.id);
  tell(s, { text: `Ringing you, then ${s.to}` });
  s.timer = setTimeout(() => endSession(s, 'timed out'), 60 * 60_000);
  return s.id;
}

export async function holdNow(id) {
  const s = sessions.get(id); if (!s) throw new Error('No such call.');
  if (!s.companySid) throw new Error('Not connected to them yet.');
  if (s.state === 'holding' || s.state === 'human') return;
  s.state = 'holding'; s.holdStartedAt = Date.now();
  // Company leg: start listening, park in the conference. Leaving the <Dial> bridge makes
  // your leg's <Dial> finish → /hold/dial-done says goodbye to you.
  const vr = new VoiceResponse();
  streamTwiml(vr, s);
  conferenceTwiml(vr, s, { endOnExit: true });
  await client.calls(s.companySid).update({ twiml: vr.toString() });
  tell(s, { text: 'Holding for you. I will ring you when a person answers.' });
}

export async function cancelHold(id) {
  const s = sessions.get(id); if (!s) return;
  await endSession(s, 'cancelled');
}

export function activeHold() {
  for (const s of sessions.values()) if (s.state !== 'ended') return { session: s.id, state: s.state, to: s.to, heard: s.heard.slice(-3) };
  return null;
}

/** Media stream (mode=hold) feeds every final transcript line here. */
export async function onHoldSpeech(id, text) {
  const s = sessions.get(id); if (!s || s.state !== 'holding') return;
  const clean = text.trim();
  const words = norm(clean).split(' ').filter(Boolean);
  if (words.length < 3) return;                                   // music / whisper noise
  s.heard.push(clean); if (s.heard.length > 50) s.heard.shift();
  const k = key(clean);
  const repeated = s.seen.has(k);
  s.seen.add(k);
  const recorded = repeated || RECORDED.test(clean);
  const sinceHold = Date.now() - (s.holdStartedAt ?? 0);
  console.log(`[hold] ${s.id} heard${recorded ? ' (recorded)' : ''}: ${clean}`);
  broadcaster?.send({ type: 'hold', session: s.id, state: s.state, to: s.to, text: clean, heard: true, recorded });
  if (recorded || sinceHold < 3000) return;
  if (!HUMAN.test(clean)) return;
  await humanDetected(s, clean);
}

async function humanDetected(s, line) {
  s.state = 'human';
  tell(s, { text: `Someone answered: “${line}”` });
  // Tell the agent, keep them in the conference (stream restarts with the new TwiML).
  const vr = new VoiceResponse();
  say(vr, "Hello — please hold for just a moment, I'm connecting you now.");
  streamTwiml(vr, s);
  conferenceTwiml(vr, s, { endOnExit: true });
  await client.calls(s.companySid).update({ twiml: vr.toString() });
  // Ring you back into the same conference.
  const call = await client.calls.create({
    to: config.userNumber.trim(),
    from: config.twilioNumber,
    url: `${config.publicUrl}/hold/ring-back?session=${s.id}`,
    statusCallback: `${config.publicUrl}/hold/status?session=${s.id}&leg=ringback`,
    statusCallbackEvent: ['answered', 'completed'],
    timeout: 40,
  });
  s.ringBackSid = call.sid; byCall.set(call.sid, s.id);
  // Keep reassuring the agent every 20 s until you join.
  const nag = setInterval(async () => {
    if (s.state !== 'human') return clearInterval(nag);
    try {
      const vr2 = new VoiceResponse();
      say(vr2, 'Thanks for holding, just one more moment.');
      streamTwiml(vr2, s); conferenceTwiml(vr2, s, { endOnExit: true });
      await client.calls(s.companySid).update({ twiml: vr2.toString() });
    } catch { clearInterval(nag); }
  }, 20_000);
  s.nag = nag;
}

async function endSession(s, reason) {
  if (s.state === 'ended') return;
  s.state = 'ended';
  clearTimeout(s.timer); if (s.nag) clearInterval(s.nag);
  for (const sid of [s.companySid, s.ringBackSid, s.userSid]) {
    if (!sid) continue;
    try { await client.calls(sid).update({ status: 'completed' }); } catch { /* already finished */ }
    byCall.delete(sid);
  }
  tell(s, { text: reason });
  setTimeout(() => sessions.delete(s.id), 60_000);
}

// ---- Twilio webhooks (TwiML + status) --------------------------------------------

export function holdRoutes(router, guard) {
  // Your mobile answered the first call: dial the company and bridge.
  router.post('/hold/you-answered', guard, (req, res) => {
    const s = sessions.get(req.query.session);
    const vr = new VoiceResponse();
    if (!s) { vr.say('Sorry, that call has expired.'); return res.type('text/xml').send(vr.toString()); }
    s.state = 'connecting'; tell(s, { text: `Calling ${s.to}…` });
    say(vr, 'Connecting you now. Tap hold for me whenever you are stuck in a queue.');
    const d = vr.dial({ callerId: config.twilioNumber, action: `${config.publicUrl}/hold/dial-done?session=${s.id}`, timeout: 40 });
    d.number({ statusCallback: `${config.publicUrl}/hold/status?session=${s.id}&leg=company`, statusCallbackEvent: ['answered', 'completed'] }, s.to);
    res.type('text/xml').send(vr.toString());
  });

  // Your first leg's <Dial> finished: either you tapped HOLD (company moved to the conference)
  // or the company hung up / didn't answer.
  router.post('/hold/dial-done', guard, (req, res) => {
    const s = sessions.get(req.query.session);
    const vr = new VoiceResponse();
    if (s && s.state === 'holding') {
      say(vr, "I'll stay on the line and ring you the moment a person answers. Goodbye for now.");
    } else if (s) {
      const st = req.body.DialCallStatus;
      say(vr, st === 'no-answer' ? 'They did not answer.' : st === 'busy' ? 'The line is busy.' : 'The call has ended.');
      endSession(s, st === 'no-answer' ? 'they did not answer' : 'call ended');
    }
    vr.hangup();
    res.type('text/xml').send(vr.toString());
  });

  // A person answered and we're ringing you back: drop you into the conference.
  router.post('/hold/ring-back', guard, (req, res) => {
    const s = sessions.get(req.query.session);
    const vr = new VoiceResponse();
    if (!s || s.state !== 'human') { vr.say('Sorry, they have gone.'); vr.hangup(); return res.type('text/xml').send(vr.toString()); }
    s.state = 'joined'; if (s.nag) clearInterval(s.nag); tell(s, { text: 'You are through.' });
    say(vr, 'They are on the line.');
    conferenceTwiml(vr, s, { endOnExit: true });
    res.type('text/xml').send(vr.toString());
  });

  // Leg status: learn the company CallSid; end the session when the important leg ends.
  router.post('/hold/status', guard, (req, res) => {
    const s = sessions.get(req.query.session);
    res.sendStatus(204);
    if (!s) return;
    const { CallSid, CallStatus } = req.body;
    const leg = req.query.leg;
    if (leg === 'company') {
      if (CallStatus === 'in-progress' || CallStatus === 'answered') {
        s.companySid = CallSid; byCall.set(CallSid, s.id);
        if (s.state === 'connecting') { s.state = 'talking'; tell(s, { text: 'Connected. Tap HOLD FOR ME when you are in the queue.' }); }
      }
      if (['completed', 'busy', 'failed', 'no-answer', 'canceled'].includes(CallStatus) && s.state !== 'ended') {
        endSession(s, s.state === 'holding' || s.state === 'human' ? 'they hung up while you were on hold' : 'call ended');
      }
    }
    if (leg === 'ringback' && ['busy', 'failed', 'no-answer', 'canceled'].includes(CallStatus) && s.state === 'human') {
      endSession(s, 'you did not pick up');
    }
    if (leg === 'user' && ['completed', 'busy', 'failed', 'no-answer', 'canceled'].includes(CallStatus)
        && ['dialing_you', 'connecting', 'talking'].includes(s.state)) {
      endSession(s, CallStatus === 'completed' ? 'call ended' : 'you did not pick up');
    }
  });
}
