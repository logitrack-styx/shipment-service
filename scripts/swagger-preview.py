"""Temporary loopback-only documentation proxy. Business paths are rejected."""
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
import subprocess
from sprint3_e2e import COMPOSE

active='security-service'
ports={'security-service':8081,'courier-service':8082,'shipment-service':8083}
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        global active
        if self.path.startswith('/__select/'):
            choice=self.path.split('/')[-1]
            if choice not in ports:self.send_error(404);return
            active=choice
            self.send_response(302);self.send_header('Location','/swagger-ui/index.html');self.end_headers();return
        if not self.path.startswith(('/swagger-ui/','/v3/api-docs','/swagger-ui.html')):
            self.send_error(404);return
        result=subprocess.run(COMPOSE+['exec','-T',active,'curl','--silent','--write-out','\n%{http_code}',
            f'http://localhost:{ports[active]}{self.path}'],capture_output=True)
        body,_,code=result.stdout.rpartition(b'\n')
        self.send_response(int(code) if code.isdigit() else 502)
        kind='application/json' if '/v3/api-docs' in self.path else 'text/html'
        if self.path.endswith('.js'):kind='application/javascript'
        if self.path.endswith('.css'):kind='text/css'
        if self.path.endswith('.png'):kind='image/png'
        self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(body)))
        self.end_headers();self.wfile.write(body)
    def log_message(self,*args):pass

ThreadingHTTPServer(('127.0.0.1',8099),Handler).serve_forever()
