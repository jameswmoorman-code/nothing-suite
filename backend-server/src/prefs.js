import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import twilio from 'twilio';
import { config } from './config.js';

/**
 * Things the user can change from the phone: the concierge's voice and greeting.
 * Kept in prefs.json next to .env (git-ignored) so they survive a restart.
 * Defaults come from .env so nothing changes for anyone who hasn't touched them.
 */
const FILE = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'prefs.json');

/** Twilio <Say> voices we offer. Neural voices sound far better; ~0.3p per greeting vs 0.08p. */
export const VOICES = Object.freeze([
  { id: 'Polly.Amy-Neural',     label: 'Amy',     desc: 'British · woman (default)' },
  { id: 'Polly.Brian-Neural',   label: 'Brian',   desc: 'British · man' },
  { id: 'Polly.Emma-Neural',    label: 'Emma',    desc: 'British · woman, softer' },
  { id: 'Polly.Arthur-Neural',  label: 'Arthur',  desc: 'British · man, deeper' },
  { id: 'Polly.Niamh-Neural',   label: 'Niamh',   desc: 'Irish · woman' },
  { id: 'Polly.Olivia-Neural',  label: 'Olivia',  desc: 'Australian · woman' },
  { id: 'Polly.Joanna-Neural',  label: 'Joanna',  desc: 'American · woman' },
  { id: 'Polly.Matthew-Neural', label: 'Matthew', desc: 'American · man' },
  { id: 'Polly.Kajal-Neural',   label: 'Kajal',   desc: 'Indian English · woman' },
  { id: 'Polly.Amy',            label: 'Amy (classic)', desc: 'British · woman, basic voice, cheapest' },
]);

const defaults = () => ({
  voice: process.env.VOICE || 'Polly.Amy-Neural',
  greeting: config.greetingText,
});

let current = defaults();
try { if (fs.existsSync(FILE)) current = { ...current, ...JSON.parse(fs.readFileSync(FILE, 'utf8')) }; }
catch (e) { console.warn(`[prefs] could not read prefs.json: ${e.message}`); }

export const prefs = {
  get voice() { return current.voice; },
  get greeting() { return current.greeting; },
  snapshot() { return { voice: current.voice, greeting: current.greeting, voices: VOICES }; },
  /** Apply what the phone sent; returns the saved snapshot. Unknown voices are refused. */
  update({ voice, greeting }) {
    if (voice !== undefined) {
      if (!VOICES.some((v) => v.id === voice)) throw new Error(`Unknown voice "${voice}"`);
      current.voice = voice;
    }
    if (greeting !== undefined) {
      const g = String(greeting).trim().slice(0, 400);
      if (g.length < 5) throw new Error('The greeting is too short.');
      current.greeting = g;
    }
    try { fs.writeFileSync(FILE, JSON.stringify({ voice: current.voice, greeting: current.greeting }, null, 2)); }
    catch (e) { console.warn(`[prefs] could not save prefs.json: ${e.message}`); }
    console.log(`[prefs] voice=${current.voice} greeting="${current.greeting}"`);
    return this.snapshot();
  },
};

/** Shorthand for TwiML: say(twiml, 'text') uses the chosen voice. */
export const say = (twiml, text) => twiml.say({ voice: prefs.voice }, text);

/**
 * Ring the user's own phone and read a sample, then hang up. ~1p.
 *   voice = one id   → that voice reads the greeting (without changing the setting)
 *   voice = 'all'    → every voice introduces itself in turn
 *   voice omitted    → the current voice
 */
export async function previewVoice(voice) {
  const to = (config.userNumber ?? '').trim();
  if (!to) throw new Error('Set MY NUMBER first so I know which phone to ring.');
  const client = twilio(config.twilio.accountSid, config.twilio.authToken);
  const vr = new twilio.twiml.VoiceResponse();
  if (voice === 'all') {
    for (const v of VOICES) {
      vr.say({ voice: v.id }, `Hello, I'm ${v.label.replace(/ \(classic\)/, '')}, ${v.desc.replace(/·/g, ',').replace(/\(default\)/, '')}. ${prefs.greeting}`);
      vr.pause({ length: 1 });
    }
    vr.say({ voice: prefs.voice }, 'That was everyone. Goodbye.');
  } else {
    const id = voice && VOICES.some((v) => v.id === voice) ? voice : prefs.voice;
    const label = VOICES.find((v) => v.id === id)?.label ?? 'the concierge';
    vr.say({ voice: id }, `Hello, I'm ${label}. ${prefs.greeting}`);
    vr.pause({ length: 1 });
    vr.say({ voice: id }, 'That is how I would sound. Goodbye.');
  }
  vr.hangup();
  await client.calls.create({ to, from: config.twilioNumber, twiml: vr.toString(), timeout: 25 });
}
