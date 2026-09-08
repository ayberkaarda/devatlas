// Configuration read once, validated at startup, and shaped so that a secret
// cannot fall out of it into a log line by accident.

'use strict';

const { inspect } = require('node:util');

class Secret {
  #value;

  constructor(value) {
    this.#value = value;
  }

  reveal() {
    return this.#value;
  }

  toString() {
    return '[redacted]';
  }

  toJSON() {
    return '[redacted]';
  }

  [inspect.custom]() {
    return '[redacted]';
  }
}

function loadConfig(env) {
  const problems = [];

  const url = env.DATABASE_URL;
  if (typeof url !== 'string' || url === '') problems.push('DATABASE_URL is required');

  const rawPort = env.PORT ?? '3000';
  const port = Number(rawPort);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    problems.push(`PORT must be a port number, got ${JSON.stringify(rawPort)}`);
  }

  const token = env.API_TOKEN;
  if (typeof token !== 'string' || token.length < 8) {
    problems.push('API_TOKEN is required and must be at least 8 characters');
  }

  if (problems.length > 0) {
    // One error naming every problem, rather than one restart per problem.
    throw new Error(`configuration is invalid:\n  - ${problems.join('\n  - ')}`);
  }

  return Object.freeze({ databaseUrl: url, port, apiToken: new Secret(token) });
}

const good = loadConfig({
  DATABASE_URL: 'postgres://localhost/app',
  PORT: '8080',
  API_TOKEN: 'sk-not-a-real-token'
});

console.log(`port is a number: ${typeof good.port} ${good.port}`);
console.log(`database url: ${good.databaseUrl}`);
console.log(`the token in a template string: ${good.apiToken}`);
console.log(`the token through JSON.stringify: ${JSON.stringify(good)}`);
console.log(`the token through util.inspect: ${inspect(good.apiToken)}`);
console.log(`the token is still readable on purpose: ${good.apiToken.reveal()}`);

console.log(`the config object is frozen: ${Object.isFrozen(good)}`);
try {
  good.port = 1;
} catch (error) {
  console.log(`writing to it throws: ${error.constructor.name}`);
}

try {
  loadConfig({ PORT: 'https://example.com', API_TOKEN: 'short' });
} catch (error) {
  console.log('loading a bad environment failed with:');
  console.log(error.message);
}
