"""Local webhook stub for the notify contract test (Python stdlib only).

Records every POST body at /hook (any path works) and exposes them at GET /events.
Run: python scripts/notify_fixture.py [port]   (default 18099)
Then start the backend with NOTIFY_MODE=live NOTIFY_PROVIDER=generic
     NOTIFY_WEBHOOK_URL=http://127.0.0.1:18099/hook
and run scripts/check_notify_contract.py against it.
"""
import json, sys
from http.server import BaseHTTPRequestHandler, HTTPServer

EVENTS = []

class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(length).decode('utf-8')
        try:
            EVENTS.append({'path': self.path, 'headers': dict(self.headers), 'body': json.loads(body)})
            print('EVENT:', body, flush=True)
            status, response = 200, '{"ok":true}'
        except json.JSONDecodeError:
            EVENTS.append({'path': self.path, 'headers': dict(self.headers), 'body': body, 'invalid': True})
            status, response = 400, '{"ok":false}'
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(response.encode())

    def do_GET(self):
        if self.path == '/events':
            payload = json.dumps(EVENTS).encode()
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(payload)
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, *args):
        pass  # keep stdout to events only / 只输出事件

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18099
    print(f'notify fixture listening on http://127.0.0.1:{port}/hook', flush=True)
    HTTPServer(('127.0.0.1', port), Handler).serve_forever()
