// A `const` binding can never be reassigned, so its literal type is kept.
const mode = "dark"; // type "dark"
// A `let` binding can be reassigned, so the literal is widened.
let theme = "dark"; // type string

type Theme = "dark" | "light";

function apply(t: Theme): string {
  return "theme:" + t;
}

console.log("const binding ->", apply(mode));
// @ts-expect-error Argument of type 'string' is not assignable to parameter of type 'Theme'.
console.log("let binding   ->", apply(theme));

// Object properties are mutable, so they widen exactly like `let`.
const config = { theme: "dark" }; // type { theme: string }
// @ts-expect-error Argument of type 'string' is not assignable to parameter of type 'Theme'.
console.log("property      ->", apply(config.theme));

// `as const` marks every member readonly, and the literal types survive.
const frozen = { theme: "dark" } as const; // type { readonly theme: "dark" }
console.log("as const      ->", apply(frozen.theme));

theme = "light";
console.log("reassigned    ->", theme);
