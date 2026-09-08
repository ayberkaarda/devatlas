interface Circle {
  kind: "circle";
  radius: number;
}
interface Square {
  kind: "square";
  side: number;
}
type Shape = Circle | Square;

// A shared literal-typed member discriminates the union.
function area(shape: Shape): number {
  switch (shape.kind) {
    case "circle":
      return Math.PI * shape.radius * shape.radius;
    case "square":
      return shape.side * shape.side;
  }
}

// The `in` operator narrows by property presence.
type Outcome = { data: string } | { error: string };
function render(outcome: Outcome): string {
  return "data" in outcome ? outcome.data : "error: " + outcome.error;
}

// `instanceof` narrows by prototype chain.
function yearOf(value: Date | string): number {
  const when = value instanceof Date ? value : new Date(value);
  return when.getUTCFullYear();
}

console.log(area({ kind: "circle", radius: 2 }).toFixed(4));
console.log(area({ kind: "square", side: 3 }));
console.log(render({ data: "ok" }));
console.log(render({ error: "gone" }));
console.log(yearOf(new Date("2019-03-01T00:00:00Z")));
console.log(yearOf("2024-11-30T00:00:00Z"));
