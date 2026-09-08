// Reading a request body. The body is a readable stream, it arrives in pieces,
// and it is as large as the client decides unless the server decides otherwise.

'use strict';

const http = require('node:http');

const MAX_BODY_BYTES = 64;

function sendJson(response, status, body) {
  const payload = JSON.stringify(body);
  response.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(payload)
  });
  response.end(payload);
}

async function readJsonBody(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > MAX_BODY_BYTES) {
      const error = new Error('body too large');
      error.code = 'payload_too_large';
      throw error;
    }
    chunks.push(chunk);
  }
  const text = Buffer.concat(chunks).toString('utf8');
  try {
    return JSON.parse(text);
  } catch {
    const error = new Error('body is not valid JSON');
    error.code = 'invalid_json';
    throw error;
  }
}

const server = http.createServer(async (request, response) => {
  if (request.method !== 'POST') {
    sendJson(response, 405, { code: 'method_not_allowed', message: 'use POST' });
    return;
  }
  try {
    const body = await readJsonBody(request);
    if (typeof body.name !== 'string' || body.name === '') {
      sendJson(response, 422, { code: 'invalid_field', message: 'name is required' });
      return;
    }
    sendJson(response, 201, { id: '3', name: body.name });
  } catch (error) {
    const status = error.code === 'payload_too_large' ? 413 : 400;
    // The client is told a code and a sentence, never the stack.
    sendJson(response, status, { code: error.code, message: error.message });
  }
});

function post(port, payload) {
  return new Promise((resolve, reject) => {
    const req = http.request(
      { host: '127.0.0.1', port, method: 'POST', path: '/records', headers: { 'content-type': 'application/json' } },
      (res) => {
        let body = '';
        res.setEncoding('utf8');
        res.on('data', (chunk) => {
          body += chunk;
        });
        res.on('end', () => resolve({ status: res.statusCode, body }));
      }
    );
    req.on('error', reject);
    req.end(payload);
  });
}

async function main() {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();

  const created = await post(port, JSON.stringify({ name: 'carol' }));
  console.log(`valid body -> ${created.status} ${created.body}`);

  const missingField = await post(port, JSON.stringify({ nickname: 'carol' }));
  console.log(`missing field -> ${missingField.status} ${missingField.body}`);

  const broken = await post(port, '{ this is not json');
  console.log(`broken JSON -> ${broken.status} ${broken.body}`);

  const huge = await post(port, JSON.stringify({ name: 'x'.repeat(200) }));
  console.log(`oversized body -> ${huge.status} ${huge.body}`);

  server.close();
}

main();
