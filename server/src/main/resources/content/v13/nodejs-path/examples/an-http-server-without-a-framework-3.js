// Three things a framework normally hides: header names are lower-cased for
// you, a query string is not an object, and a response can only start once.

'use strict';

const http = require('node:http');

const observed = {};

const server = http.createServer((request, response) => {
  const url = new URL(request.url, 'http://localhost');

  observed.mixedCaseLookup = request.headers['X-Request-Id'];
  observed.lowerCaseLookup = request.headers['x-request-id'];
  observed.headerKeysAreLowerCased = Object.keys(request.headers).every((k) => k === k.toLowerCase());
  observed.repeatedHeaderJoined = request.headers['x-tag'];
  observed.repeatedHeaderSeparately = request.headersDistinct['x-tag'];

  // A query string is text. searchParams.get returns the first value; getAll
  // returns every one, and a missing parameter is null rather than undefined.
  observed.singleValue = url.searchParams.get('tag');
  observed.allValues = url.searchParams.getAll('tag');
  observed.missingParameter = url.searchParams.get('absent');
  observed.numbersAreStrings = typeof url.searchParams.get('limit');
  observed.decoded = url.searchParams.get('name');

  response.writeHead(200, { 'content-type': 'text/plain; charset=utf-8' });
  response.end('ok');

  // The status line and headers have already gone out. There is nothing left to
  // change, and Node.js says so rather than silently doing nothing.
  observed.headersSentAfterEnd = response.headersSent;
  try {
    response.writeHead(500);
  } catch (error) {
    observed.secondWriteHeadCode = error.code;
  }
  try {
    response.setHeader('x-late', '1');
  } catch (error) {
    observed.lateSetHeaderCode = error.code;
  }
});

function get(port, path, headers) {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port, method: 'GET', path, headers }, (res) => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => {
        body += chunk;
      });
      res.on('end', () => resolve({ status: res.statusCode, body }));
    });
    req.on('error', reject);
    req.end();
  });
}

async function main() {
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();

  const response = await get(port, '/search?tag=red&tag=blue&limit=10&name=ada%20lovelace', {
    'X-Request-Id': 'abc-123',
    'x-tag': ['one', 'two']
  });

  console.log(`response: ${response.status} ${response.body}`);
  console.log(`header keys are lower-cased: ${observed.headerKeysAreLowerCased}`);
  console.log(`lookup with the casing the client sent: ${observed.mixedCaseLookup}`);
  console.log(`lookup in lower case: ${observed.lowerCaseLookup}`);
  console.log(`repeated header, joined: ${observed.repeatedHeaderJoined}`);
  console.log(`repeated header, kept apart: ${JSON.stringify(observed.repeatedHeaderSeparately)}`);
  console.log(`searchParams.get('tag'): ${observed.singleValue}`);
  console.log(`searchParams.getAll('tag'): ${JSON.stringify(observed.allValues)}`);
  console.log(`a missing parameter is: ${observed.missingParameter}`);
  console.log(`typeof the 'limit' parameter: ${observed.numbersAreStrings}`);
  console.log(`percent-encoding is decoded: ${observed.decoded}`);
  console.log(`headersSent after end(): ${observed.headersSentAfterEnd}`);
  console.log(`second writeHead threw: ${observed.secondWriteHeadCode}`);
  console.log(`late setHeader threw: ${observed.lateSetHeaderCode}`);

  server.close();
}

main();
