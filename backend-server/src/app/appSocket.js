import WebSocket from 'ws';
import { applyAction, registry } from '../twilio/callControl.js';
import { startHold, holdNow, cancelHold, activeHold, attachHoldBroadcaster } from '../hold/holdForMe.js';

/**
 * Fan-out of transcript events to every connected phone (usually one).
 *
 * Server → phone (JSON, one object per frame):
 *   { type: 'hello' | 'call_started' | 'assistant' | 'delta' | 'final' | 'call_ended' | 'error',
 *     callSid, from, text?, ts }
 * Phone → server:
 *   { type: 'action', callSid, action: 'ask_reason'|'callback'|'message'|'hangup'|'connect'|'say', text?, to? }
 */
export class AppBroadcaster {
  #wss;

  constructor(wss) {
    this.#wss = wss;
    attachHoldBroadcaster(this);
    wss.on('connection', (ws, req) => {
      console.log(`[app] phone connected from ${req.socket.remoteAddress}`);
      ws.isAlive = true;
      ws.on('pong', () => (ws.isAlive = true));
      ws.send(JSON.stringify({ type: 'hello', ts: Date.now() }));
      // The phone's link drops when it sleeps; if a call is already in progress, replay it
      // so a reconnecting phone pops the live screen / Glyph instead of missing the call.
      for (const callSid of registry.active()) {
        const from = registry.from(callSid) ?? 'unknown';
        ws.send(JSON.stringify({ type: 'call_started', callSid, from, ts: Date.now(), resumed: true }));
      }
      const hold = activeHold();
      if (hold) ws.send(JSON.stringify({ type: 'hold', ...hold, ts: Date.now() }));
      ws.on('message', async (raw) => {
        let msg;
        try { msg = JSON.parse(raw.toString()); } catch { return; }
        try {
          if (msg.type === 'hold_start') { const id = await startHold(String(msg.to ?? '')); return; }
          if (msg.type === 'hold_now') { await holdNow(msg.session); return; }
          if (msg.type === 'hold_cancel') { await cancelHold(msg.session); return; }
          if (msg.type !== 'action') return;
          await applyAction(msg, this);
        } catch (e) {
          console.warn(`[action] failed: ${e.message}`);
          ws.send(JSON.stringify({ type: 'error', callSid: msg.callSid, text: e.message, ts: Date.now() }));
        }
      });
    });

    // Keep-alive so mobile networks don't silently drop the socket.
    setInterval(() => {
      for (const ws of wss.clients) {
        if (!ws.isAlive) return ws.terminate();
        ws.isAlive = false;
        ws.ping();
      }
    }, 25_000).unref();
  }

  send(event) {
    const frame = JSON.stringify({ ...event, ts: Date.now() });
    for (const ws of this.#wss.clients) {
      if (ws.readyState === WebSocket.OPEN) ws.send(frame);
    }
  }
}
