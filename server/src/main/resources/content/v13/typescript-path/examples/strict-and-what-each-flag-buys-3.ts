// strictPropertyInitialization (part of --strict) refuses a declared property
// that no constructor path assigns.
class Session {
  // @ts-expect-error Property 'token' has no initializer and is not definitely assigned in the constructor.
  token: string;
}
void Session;

// The definite assignment assertion silences the check. It asserts; it does not
// arrange for anything to be assigned.
class AssertedSession {
  token!: string;
  size(): number {
    return this.token.length;
  }
}

try {
  console.log(new AssertedSession().size());
} catch (error) {
  console.log("runtime failure:", (error as Error).message);
}

class SafeSession {
  constructor(public readonly token: string) {}
  size(): number {
    return this.token.length;
  }
}

console.log(new SafeSession("abc").size());
