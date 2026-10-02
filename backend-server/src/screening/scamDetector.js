/**
 * Scam shield — scores what the caller says, live, against the patterns UK
 * phone scams actually use. Pure text rules, no AI call, no data kept: each
 * call's score lives in memory until the call ends.
 *
 * Levels:  none → caution (score ≥ 3) → scam (score ≥ 6)
 * A level is only ever raised during a call, never lowered, so the warning
 * doesn't flicker away while the caller changes tack.
 */

// [regex, weight, what we tell the user]
const RULES = [
  // Who they claim to be
  [/\b(hmrc|inland revenue|tax (office|rebate|refund)|national insurance)\b/i, 3, 'claims to be HMRC / tax office'],
  [/\b(fraud (team|department|squad)|security (team|department)|bank(ing)? security)\b/i, 3, 'claims to be a bank fraud team'],
  [/\b(amazon prime|prime membership|your (amazon|netflix|sky|bt|virgin|ee|o2|vodafone) (account|subscription))\b/i, 2, 'claims to be about a subscription / account'],
  [/\b(microsoft|windows|apple|bt) (support|technical|security)\b/i, 3, 'claims to be tech support'],
  [/\b(dvla|dwp|home office|border force|police|court|bailiff|ofgem|energy (rebate|grant|scheme))\b/i, 2, 'claims to be a government body or police'],
  [/\b(parcel|delivery|package) (is|was|has been) (held|delayed|returned|undeliverable)\b/i, 2, 'parcel-held story'],
  [/\b(accident|injury|crash) (that )?(wasn'?t|was not) your fault\b/i, 3, 'accident-claim cold call'],
  [/\b(investment|crypto|bitcoin|forex|trading platform|guaranteed (returns?|profit))\b/i, 3, 'investment / crypto pitch'],
  [/\b(prize|winner|won a|lottery|free (holiday|cruise|gift))\b/i, 3, 'prize / lottery story'],

  // What they want
  [/\b(gift|itunes|google play|steam|amazon) (card|voucher)s?\b/i, 5, 'asks for gift cards or vouchers'],
  [/\b(one[- ]time (passcode|code|password)|otp|code (we|i|they) (just )?(sent|texted)|read (me|out|back) the code|verification code)\b/i, 5, 'asks for a one-time code'],
  [/\b(pin|password|passcode|memorable (word|information)|security (question|answer))\b/i, 3, 'asks for a PIN or password'],
  [/\b(card (number|details)|long number on|sort code|account number|cvv|expiry date|security code on the back)\b/i, 4, 'asks for card or bank details'],
  [/\b(date of birth|mother'?s maiden name|national insurance number|passport number|driving licence number)\b/i, 3, 'asks for identity details'],
  [/\b(safe account|holding account|secure account|move (your|the) (money|funds|savings)|transfer (it|the money|your money|the funds)|protect your (money|funds|savings)\b)/i, 6, '"move your money to a safe account"'],
  [/\b(anydesk|teamviewer|quick ?support|remote (access|control|desktop)|download (an? )?(app|software)|install (an? )?(app|software)|share your screen)\b/i, 5, 'wants remote access to your device'],
  [/\b(press (one|1|two|2|three|3)|press any key)\b/i, 2, '"press 1" robocall'],
  [/\b(pay(ment)? (now|today|immediately)|outstanding (balance|payment|fine|fee)|arrears|unpaid (tax|fine|bill))\b/i, 2, 'demands immediate payment'],
  [/\b(bank transfer|wire transfer|western union|moneygram|bitcoin atm|crypto ?atm|paypal friends)\b/i, 3, 'untraceable payment method'],
  [/\b(withdraw (cash|the money|your money)|courier|someone will (come|collect)|hand (it|the cash|the card) (over|to))\b/i, 6, 'courier / cash collection'],

  // How they pressure
  [/\b(arrest(ed)?|warrant|prosecut(e|ion)|legal action|court (action|summons)|deport(ed|ation)|police (will|are) (be )?(coming|on their way))\b/i, 4, 'threatens arrest or legal action'],
  [/\b(suspend(ed)?|block(ed)?|frozen|freeze|closed|terminated?|cancell?ed) (your|the) (account|card|number|service|licence)|will be (suspended|blocked|frozen|closed|cancelled)\b/i, 3, 'threatens to suspend your account'],
  [/\b(immediately|right now|straight ?away|urgent(ly)?|within (the next )?(\d+|one|two|few|24) (minutes?|hours?)|before (midnight|the end of (the day|today))|last (chance|warning|notice)|final (notice|warning|reminder))\b/i, 2, 'creates urgency'],
  [/\b(do(n'?t| not) (tell|inform|speak to|discuss|mention)|keep (this|it) (confidential|between us|secret)|(this|it) is confidential|don'?t hang up|stay on the line)\b/i, 4, 'tells you to keep it secret or stay on the line'],
  [/\b(compromised|hacked|suspicious (activity|transaction|login)|unusual activity|unauthoris?ed (transaction|payment|access)|someone (has )?(tried|attempted|is trying) to)\b/i, 3, '"your account has been compromised" story'],
  [/\b(confirm (your|the) (identity|details)|verify (your|the) (identity|details|account)|security check|for security (purposes|reasons))\b/i, 2, 'asks you to "verify" yourself'],
];

export const LEVELS = ['none', 'caution', 'scam'];
const THRESHOLDS = { caution: 3, scam: 6 };

export function levelFor(score) {
  if (score >= THRESHOLDS.scam) return 'scam';
  if (score >= THRESHOLDS.caution) return 'caution';
  return 'none';
}

/** Score one utterance. Returns [{weight, reason}] for every rule it trips. */
export function scoreText(text) {
  const hits = [];
  for (const [re, weight, reason] of RULES) if (re.test(text)) hits.push({ weight, reason });
  return hits;
}

class CallRisk {
  score = 0;
  level = 'none';
  reasons = [];       // unique, in the order they appeared
  add(text) {
    let changed = false;
    for (const { weight, reason } of scoreText(text)) {
      if (!this.reasons.includes(reason)) { this.reasons.push(reason); this.score += weight; changed = true; }
    }
    const next = levelFor(this.score);
    const raised = LEVELS.indexOf(next) > LEVELS.indexOf(this.level);
    if (raised) this.level = next;
    return { changed, raised };
  }
}

const calls = new Map();

/**
 * Feed a finished caller sentence. Returns an alert frame to broadcast when the
 * level goes up or a new reason appears while already on alert, else null.
 */
export function assessUtterance(callSid, text) {
  if (!text?.trim()) return null;
  let risk = calls.get(callSid);
  if (!risk) { risk = new CallRisk(); calls.set(callSid, risk); }
  const { changed, raised } = risk.add(text);
  if (raised || (changed && risk.level !== 'none')) {
    console.log(`[scam] ${callSid} → ${risk.level.toUpperCase()} (${risk.score}): ${risk.reasons.join('; ')}`);
    return { type: 'alert', callSid, level: risk.level, score: risk.score, reasons: [...risk.reasons] };
  }
  return null;
}

export function riskFor(callSid) { return calls.get(callSid) ?? null; }
export function forgetCall(callSid) { calls.delete(callSid); }
