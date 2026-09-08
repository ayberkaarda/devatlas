interface Circle {
  kind: "circle";
  radius: number;
}
interface Square {
  kind: "square";
  side: number;
}
interface Rect {
  kind: "rect";
  width: number;
  height: number;
}
type Shape = Circle | Square | Rect;

function area(shape: Shape): number {
  switch (shape.kind) {
    case "circle":
      return Math.PI * shape.radius * shape.radius;
    case "square":
      return shape.side * shape.side;
    case "rect":
      return shape.width * shape.height;
    default: {
      // Every member is handled above, so control flow analysis reduces `shape`
      // to `never` here and the assignment is legal.
      const exhaustive: never = shape;
      return exhaustive;
    }
  }
}

console.log(area({ kind: "circle", radius: 2 }).toFixed(4));
console.log(area({ kind: "square", side: 3 }));
console.log(area({ kind: "rect", width: 2, height: 5 }));
