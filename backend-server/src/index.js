/**
 * Nothing Suite — telephony engine.
 *
 *   Caller ──► carrier CFB (*67*) ──► your Twilio number ──► POST /voice (TwiML)
 *                                           │
 *                                           └─► Media Stream  ──► wss://…/twilio-media
 *                                                                        │ μ-law 8 kHz
 *                                                                        ▼
 *                                                             OpenAI Realtime transcription
 *                                                                        │ text deltas
 *                                                                        ▼
 *                                                Android app ◄── wss://…/app?token=SECRET
 *
 * Runs on your own machine (laptop, Raspberry Pi, free-tier VPS). No data is stored.
 */
import http from 'node:http';
import express from 'express';
import { WebSocketServer } from 'ws';

import { config } from './config.js';
import { voiceRouter } from './twilio/voiceWebhook.js';
import { handleTwilioMediaSocket } from './twilio/mediaStream.js';
import { AppBroadcaster } from './app/appSocket.js';

const app = express();
app.use(express.urlencoded({ extended: false })); // Twilio posts form-encoded bodies
app.use(express.json());

app.get('/healthz', (_req, res) => res.json({ ok: true, ts: Date.now() }));
app.use('/', voiceRouter);

const server = http.createServer(app);

// Two WebSocket endpoints share one HTTP server; we route on the upgrade path.
const twilioWss = new WebSocketServer({ noServer: true });
const appWss = new WebSocketServer({ noServer: true });
const broadcaster = new AppBroadcaster(appWss);

server.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  if (url.pathname === '/twilio-media') {
    // Twilio cannot send custom auth headers on Media Streams; the stream is
    // protected by (a) the signed /voice webhook that mints it and (b) the
    // random streamSid Twilio generates. Keep PUBLIC_URL private.
    twilioWss.handleUpgrade(req, socket, head, (ws) => twilioWss.emit('connection', ws, req));
    return;
  }

  if (url.pathname === '/app') {
    const token = url.searchParams.get('token');
    if (token !== config.appSharedSecret) {
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      socket.destroy();
      return;
    }
    appWss.handleUpgrade(req, socket, head, (ws) => appWss.emit('connection', ws, req));
    return;
  }

  socket.destroy();
});

twilioWss.on('connection', (ws) => handleTwilioMediaSocket(ws, broadcaster));

server.listen(config.port, () => {
  console.log(`[server] listening on :${config.port}`);
  console.log(`[server] Twilio voice webhook → ${config.publicUrl}/voice`);
  console.log(`[server] App transcript socket → ${config.publicUrl.replace(/^http/, 'ws')}/app?token=…`);
});
