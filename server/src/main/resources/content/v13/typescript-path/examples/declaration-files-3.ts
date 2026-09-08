export {};

// Declaration merging: `declare global` reaches out of this module and adds a
// member to an interface declared elsewhere.
declare global {
  interface Array<T> {
    firstOrNull(): T | null;
  }
}

// The declaration buys nothing until something supplies the behaviour.
Array.prototype.firstOrNull = function <T>(this: T[]): T | null {
  return this.length > 0 ? this[0] : null;
};

console.log(["a", "b"].firstOrNull());
console.log([1, 2].firstOrNull());
console.log(([] as string[]).firstOrNull());

// Two interface declarations with the same name in the same scope merge.
interface Config {
  host: string;
}
interface Config {
  port: number;
}

const config: Config = { host: "localhost", port: 5432 };
console.log(config.host + ":" + config.port);

// Merging is not overriding: a member cannot be redeclared with a new type.
interface Config {
  // @ts-expect-error Subsequent property declarations must have the same type.
  host: number;
}
