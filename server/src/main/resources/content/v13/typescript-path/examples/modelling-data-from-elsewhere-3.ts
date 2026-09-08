interface Line {
  sku: string;
  qty: number;
}
interface Order {
  id: string;
  lines: Line[];
}

// This guard inspects the top level only. It is a real check, and it is not
// enough: nothing below the first level was looked at.
function isOrderShallow(value: unknown): value is Order {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  return typeof record.id === "string" && Array.isArray(record.lines);
}

const payload: unknown = JSON.parse(
  '{"id":"o-1","lines":[{"sku":"a","qty":"2"},{"sku":"b","qty":"3"}]}',
);

if (isOrderShallow(payload)) {
  const total = payload.lines.reduce(function (sum, line) {
    return sum + line.qty;
  }, 0);
  console.log("shallow total:", total, "(typeof", typeof total + ")");
}

function isLine(value: unknown): value is Line {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const record = value as Record<string, unknown>;
  return typeof record.sku === "string" && typeof record.qty === "number";
}

function isOrder(value: unknown): value is Order {
  if (!isOrderShallow(value)) {
    return false;
  }
  return (value as Order).lines.every(isLine);
}

console.log("deep guard accepts:", isOrder(payload));
