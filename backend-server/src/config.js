import 'dotenv/config';

function required(name) {
  const v = process.env[name];
  if (!v) {
    console.error(`[config] Missing required env var ${name}. Copy .env.example to .env and fill it in.`);
    process.exit(1);
  }
  return v;
}

const transcriber = (process.env.TRANSCRIBER ?? 'local').toLowerCase();
if (!['local', 'openai'].includes(transcriber)) {
  console.error(`[config] TRANSCRIBER must be "local" or "openai", got "${transcriber}"`);
  process.exit(1);
}

export const config = Object.freeze({
  port: Number(process.env.PORT ?? 8080),
  publicUrl: required('PUBLIC_URL').replace(/\/$/, ''),

  twilio: {
    accountSid: required('TWILIO_ACCOUNT_SID'),
    authToken: required('TWILIO_AUTH_TOKEN'),
    validateSignature: (process.env.TWILIO_VALIDATE_SIGNATURE ?? 'true') !== 'false',
  },

  /** "local" = free, on this machine (faster-whisper). "openai" = paid per minute, lowest latency. */
  transcriber,

  local: {
    pythonBin: process.env.PYTHON_BIN ?? 'python3',
    model: process.env.LOCAL_WHISPER_MODEL ?? 'base.en',
  },

  openai: {
    apiKey: transcriber === 'openai' ? required('OPENAI_API_KEY') : (process.env.OPENAI_API_KEY ?? ''),
    transcribeModel: process.env.OPENAI_TRANSCRIBE_MODEL ?? 'gpt-4o-mini-transcribe',
  },

  appSharedSecret: required('APP_SHARED_SECRET'),
  greetingText:
    process.env.GREETING_TEXT ??
    'Hi, this call is being screened. Please say who you are and why you are calling.',
});

/** wss:// version of PUBLIC_URL for TwiML <Stream url="..."> */
export const publicWsUrl = config.publicUrl.replace(/^http/, 'ws');
