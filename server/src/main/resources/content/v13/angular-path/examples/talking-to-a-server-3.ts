// Where a failure belongs: turned into a code at the edge, turned into a
// translation key at the screen, and never shown to a reader as prose the
// server wrote.
//
// The mapping is a pure function, so it can be run and printed here without a
// framework, a server or a browser.

const TRANSLATED_CODES: readonly string[] = [
  'NETWORK_UNAVAILABLE',
  'TRACK_NOT_FOUND',
  'ACCESS_TOKEN_EXPIRED',
  'RATE_LIMITED',
  'INTERNAL_ERROR',
];

/** A code the interface has no words for is reported as the generic failure. */
function codeKey(code: string): string {
  return `error.${TRANSLATED_CODES.includes(code) ? code : 'INTERNAL_ERROR'}`;
}

interface Failure {
  readonly status: number;
  readonly body: { readonly code?: string; readonly message?: string } | null;
}

function codeOf(failure: Failure): string {
  // Status nought is a request that never reached the server. It says nothing
  // about the request, so it must not be reported as anything the server sent.
  if (failure.status === 0) {
    return 'NETWORK_UNAVAILABLE';
  }
  return failure.body?.code ?? 'INTERNAL_ERROR';
}

const failures: readonly Failure[] = [
  { status: 0, body: null },
  { status: 404, body: { code: 'TRACK_NOT_FOUND', message: 'No track with slug angular-path' } },
  { status: 401, body: { code: 'ACCESS_TOKEN_EXPIRED', message: 'Token expired at 12:04' } },
  { status: 500, body: { code: 'DATABASE_POOL_EXHAUSTED', message: 'HikariPool-1 timeout' } },
  { status: 502, body: null },
];

for (const failure of failures) {
  const code = codeOf(failure);
  console.log(`${failure.status} -> ${code} -> ${codeKey(code)}`);
}
