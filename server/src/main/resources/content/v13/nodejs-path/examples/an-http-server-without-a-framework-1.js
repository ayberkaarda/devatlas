// A server, a router, and the responses a framework would have produced for
// you. The server listens on port 0 so the operating system chooses a free
// port; the number it chose is never printed, because it differs every run.

'use strict';

const http = require('node:http');

const records = new Map([
  ['1', { id: '1', name: 'alice' }],
  ['2', { id: '2', name: 'bob' }]
]);

function sendJson(response, status, body) {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload)
  });
  response.end(payload);
}

const server = http.createServer((request, response) => {
  // req.url is a path and query, never a full URL, so it needs a base to parse.
  const url = new URL(request.url, 'http://localhost');

  if (url.pathname === '/records') {
    if (request.method !== 'GET') {
      response.setHeader('allow', 'GET');
      sendJson(response, 405, { code: 'method_not_allowed', message: 'use GET' });
      return;
    }
    sendJson(response, 200, [...records.values()]);
    return;
  }

  const match = /^\/records\/([^/]+)$/.exec(url.pathname);
  if (match) {
    const record = records.get(match[1]);
    if (record === undefined) {
      sendJson(response, 404, { code: 'not_found', message: 'no such record' });
      return;
    }
    sendJson(response, 200, record);
    return;
  }

  sendJson(response, 404, { code: 'not_found', message: 'no such route' });
});

function request(port, method, path) {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port, method, path }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => {
        body += chunk;
      });
      res.on('end', () =>
        resolve({ status: res.statusCode, contentType: res.headers['content-type'], allow: res.headers.allow, body })
      );
    });
    req.on('error', reject);
    req.end();
  });
}

async function main() {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();

  const list = await request(port, 'GET', '/records');
  console.log(`GET /records -> ${list.status}`);
  console.log(`content-type: ${list.contentType}`);
  console.log(`body: ${list.body}`);

  const one = await request(port, 'GET', '/records/2');
  console.log(`GET /records/2 -> ${one.status} ${one.body}`);

  const missing = await request(port, 'GET', '/records/99');
  console.log(`GET /records/99 -> ${missing.status} ${missing.body}`);

  const wrongMethod = await request(port, 'DELETE', '/records');
  console.log(`DELETE /records -> ${wrongMethod.status} ${wrongMethod.body}`);
  console.log(`allow header: ${wrongMethod.allow}`);

  const nowhere = await request(port, 'GET', '/nothing/here');
  console.log(`GET /nothing/here -> ${nowhere.status} ${nowhere.body}`);

  server.close();
}

main();
