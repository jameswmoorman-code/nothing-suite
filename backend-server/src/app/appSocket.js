import WebSocket from 'ws';

/**
 * Fan-out of transcript events to every connected phone (usually one).
 *
 * Wire format (JSON, one object per frame):
 *   { type: 'call_started' | 'delta' | 'final' | 'call_ended' | 'error',
 *     callSid, from, text?, ts }
 */
export class AppBroadcaster {
  #wss;

  constructor(wss) {
    this.#wss = wss;
    wss.on('connection', (ws, req) => {
      console.log(`[app] phone connected from ${req.socket.remoteAddress}`);
      ws.isAlive = true;
      ws.on('pong', () => (ws.isAlive = true));
      ws.send(JSON.stringify({ type: 'hello', ts: Date.now() }));
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
