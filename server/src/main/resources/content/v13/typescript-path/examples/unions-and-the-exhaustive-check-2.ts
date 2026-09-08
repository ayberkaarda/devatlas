interface Circle {
  kind: "circle";
  radius: number;
}
interface Square {
  kind: "square";
  side: number;
}
interface Triangle {
  kind: "triangle";
  base: number;
  height: number;
}
// A member was added to the union and no branch was added to the switch.
type Shape = Circle | Square | Triangle;

function area(shape: Shape): number {
  switch (shape.kind) {
    case "circle":
      return Math.PI * shape.radius * shape.radius;
    case "square":
      return shape.side * shape.side;
    default: {
      // @ts-expect-error Type 'Triangle' is not assignable to type 'never'.
      const exhaustive: never = shape;
      void exhaustive;
      return 0;
    }
  }
}

console.log(area({ kind: "square", side: 4 }));
// Silencing the diagnostic does not make the answer right.
console.log(area({ kind: "triangle", base: 3, height: 4 }));
