import { createServer } from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../out');
const arg = process.argv.find((value) => value.startsWith('--port='));
const portIndex = process.argv.indexOf('--port');
const port = Number(arg?.split('=')[1] ?? (portIndex >= 0 ? process.argv[portIndex + 1] : 3100));
const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png', '.webp': 'image/webp', '.mp4': 'video/mp4', '.woff2': 'font/woff2', '.ico': 'image/x-icon', '.json': 'application/json' };

createServer((request, response) => {
  let pathname = decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname);
  if (pathname.endsWith('/')) pathname += 'index.html';
  let target = path.resolve(root, `.${pathname}`);
  if (!target.startsWith(root + path.sep) && target !== path.join(root, 'index.html')) { response.writeHead(403).end(); return; }
  if (!fs.existsSync(target) || !fs.statSync(target).isFile()) {
    const nested = path.join(target, 'index.html');
    if (fs.existsSync(nested)) target = nested;
    else { response.writeHead(404).end('Not found'); return; }
  }
  response.writeHead(200, { 'Content-Type': types[path.extname(target)] ?? 'application/octet-stream' });
  fs.createReadStream(target).pipe(response);
}).listen(port, '127.0.0.1', () => console.log(`Static export on http://127.0.0.1:${port}`));
