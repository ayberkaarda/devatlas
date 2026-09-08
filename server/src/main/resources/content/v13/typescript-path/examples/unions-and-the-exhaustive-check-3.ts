interface Circle {
  kind: "circle";
  radius: number;
}
interface Square {
  kind: "square";
  side: number;
}
type Shape = Circle | Square;

function assertNever(value: never): never {
  throw new Error("unhandled shape: " + JSON.stringify(value));
}

function area(shape: Shape): number {
  switch (shape.kind) {
    case "circle":
      return Math.PI * shape.radius * shape.radius;
    case "square":
      return shape.side * shape.side;
    default:
      return assertNever(shape);
  }
}

console.log(area({ kind: "square", side: 2 }));

// The exhaustiveness check covers values the type system saw. A payload that
// was asserted into the type rather than validated reaches the default branch.
const fromNetwork = JSON.parse(
  '{"kind":"triangle","base":3,"height":4}',
) as Shape;

try {
  console.log(area(fromNetwork));
} catch (error) {
  console.log("runtime failure:", (error as Error).message);
}
