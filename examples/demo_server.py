#!/usr/bin/env python3
"""Tiny standard-library consumer used by the DeliveryDrill demo.

Run with `python examples/demo_server.py --mode resilient` (or vulnerable).
It intentionally has no production dependencies.
"""
import argparse, hashlib, hmac, json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

STATE = {"status": "CREATED", "processed_events": 0, "transactions": []}
SEEN = set()
RANK = {"created": 0, "processing": 1, "completed": 2, "failed": 2, "refunded": 3}

class Handler(BaseHTTPRequestHandler):
    mode = "vulnerable"
    secret = ""
    def _json(self, code, value):
        body = json.dumps(value).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        if self.path.rstrip("/") == "/payments/P123": self._json(200, STATE)
        else: self._json(404, {"error": "not found"})
    def do_POST(self):
        if self.path.rstrip("/") != "/webhooks": self._json(404, {"error": "not found"}); return
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        if self.mode == "resilient" and self.secret:
            expected = hmac.new(self.secret.encode(), body, hashlib.sha256).hexdigest()
            if not hmac.compare_digest(expected, self.headers.get("X-Signature", "")): self._json(401, {"error": "invalid signature"}); return
        try: event = json.loads(body)
        except json.JSONDecodeError: self._json(400, {"error": "malformed json"}); return
        event_id = self.headers.get("X-Event-ID", event.get("id", ""))
        event_type = self.headers.get("X-Event-Type", event.get("type", "")).lower()
        event_state = event_type.rsplit(".", 1)[-1]
        if self.mode == "resilient" and event_id in SEEN: self._json(200, {"duplicate": True}); return
        if self.mode == "resilient": SEEN.add(event_id)
        STATE["processed_events"] += 1
        if self.mode == "vulnerable" or RANK.get(event_state, 0) >= RANK.get(STATE["status"].lower(), 0): STATE["status"] = event_state.upper()
        if self.mode == "vulnerable" or not any(t.get("event_id") == event_id for t in STATE["transactions"]): STATE["transactions"].append({"event_id": event_id})
        self._json(202, {"accepted": True})
    def log_message(self, *_): pass

class DemoServer(ThreadingHTTPServer):
    # Keep the local demo deterministic when a barrier releases many clients at once.
    request_queue_size = 128

if __name__ == "__main__":
    parser = argparse.ArgumentParser(); parser.add_argument("--mode", choices=["vulnerable", "resilient"], default="vulnerable"); parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args(); Handler.mode = args.mode; Handler.secret = __import__("os").environ.get("WEBHOOK_SECRET", "")
    print(f"{args.mode} demo consumer listening on http://localhost:{args.port}", flush=True); DemoServer(("localhost", args.port), Handler).serve_forever()
