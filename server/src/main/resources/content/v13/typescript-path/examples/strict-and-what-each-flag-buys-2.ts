// useUnknownInCatchVariables (part of --strict) types the catch variable as
// unknown, because a `throw` may carry any value at all.
function classify(run: () => void): string {
  try {
    run();
    return "ok";
  } catch (error) {
    if (error instanceof Error) {
      return "Error: " + error.message;
    }
    return "thrown non-error: " + String(error);
  }
}

// Never called. Without the directive this function does not compile.
function unchecked(run: () => void): string {
  try {
    run();
    return "ok";
  } catch (error) {
    // @ts-expect-error 'error' is of type 'unknown'.
    return error.message;
  }
}
void unchecked;

// noImplicitAny (part of --strict) refuses a parameter it cannot type.
// @ts-expect-error Parameter 'x' implicitly has an 'any' type.
function double(x) {
  return x * 2;
}

console.log(
  classify(function () {
    throw new Error("boom");
  }),
);
console.log(
  classify(function () {
    throw "a bare string";
  }),
);
console.log(classify(function () {}));
console.log(double(21));
