// Expressions only, and a sandboxed scope.
//
// Vue 3.5's template syntax documentation states that each binding "can only
// contain one single expression", and that template expressions "are sandboxed
// and only have access to a restricted list of globals". Both rules are
// enforceable in plain JavaScript, so both can be watched to fire here. This
// models the rules; it is not Vue's compiler.

const ALLOWED_GLOBALS = new Set(["Math", "Date", "JSON", "String", "Number"]);

function scopeFor(state) {
  // `has` answering true for every key is what keeps the scope chain from
  // reaching the real global object -- the same trick Vue's runtime compiler
  // uses to sandbox a `with` block.
  return new Proxy(state, {
    has: (_target, key) => typeof key !== "symbol",
    get(target, key) {
      // `with` asks for this symbol before every name lookup; answering
      // undefined means "nothing here is unscopable", which is what we want.
      if (key === Symbol.unscopables) {
        return undefined;
      }
      if (key in target) {
        return target[key];
      }
      if (ALLOWED_GLOBALS.has(key)) {
        return globalThis[key];
      }
      throw new ReferenceError(`not exposed to the template: ${String(key)}`);
    },
  });
}

function compileExpression(source) {
  return new Function("$scope", `with ($scope) { return (${source}) }`);
}

function attempt(source, state) {
  try {
    const value = compileExpression(source)(scopeFor(state));
    return { ok: true, value };
  } catch (error) {
    return { ok: false, kind: error.constructor.name };
  }
}

const state = { number: 1, ok: true, message: "yes" };

// A property nobody exposed to the template, attached to the real global.
globalThis.appName = "attached to the global object";

for (const source of [
  "number + 1",
  "ok ? 'YES' : 'NO'",
  "Math.max(number, 41)",
  "var a = 1",
  "if (ok) { return message }",
  "appName",
]) {
  const result = attempt(source, state);
  const outcome = result.ok
    ? `evaluated to ${JSON.stringify(result.value)}`
    : `rejected with ${result.kind}`;
  console.log(`{{ ${source} }} -> ${outcome}`);
}

console.log(`statements are rejected before running: ${!attempt("var a = 1", state).ok}`);
console.log(`allow-listed globals reach the template: ${attempt("Math.PI > 3", state).ok}`);
console.log(`a global nobody exposed does not: ${!attempt("appName", state).ok}`);
