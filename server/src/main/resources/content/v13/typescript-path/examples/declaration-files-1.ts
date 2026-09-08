// An ambient declaration describes something the runtime already provides. It
// contributes types and emits no code at all.
declare const process: {
  readonly version: string;
  readonly argv: string[];
};

console.log("version is a string:", typeof process.version === "string");
console.log("version starts with:", process.version.charAt(0));
console.log("argv is an array:", Array.isArray(process.argv));

// The declaration is the only thing the checker consults. It says nothing about
// members it did not list, so this is refused even though the runtime has one.
// @ts-expect-error Property 'platform' does not exist on the declared type.
const platform: unknown = process.platform;
console.log("platform exists at runtime:", typeof platform === "string");
