interface User {
  id: string;
  name: string;
  roles: string[];
}

type ParseResult<T> = { ok: true; value: T } | { ok: false; error: string };

function isStringArray(value: unknown): value is string[] {
  return (
    Array.isArray(value) &&
    value.every(function (item) {
      return typeof item === "string";
    })
  );
}

function parseUser(text: string): ParseResult<User> {
  let raw: unknown;
  try {
    raw = JSON.parse(text);
  } catch (error) {
    void error;
    return { ok: false, error: "not JSON" };
  }
  if (typeof raw !== "object" || raw === null) {
    return { ok: false, error: "not an object" };
  }
  const record = raw as Record<string, unknown>;
  if (typeof record.id !== "string") {
    return { ok: false, error: "id must be a string" };
  }
  if (typeof record.name !== "string") {
    return { ok: false, error: "name must be a string" };
  }
  if (!isStringArray(record.roles)) {
    return { ok: false, error: "roles must be an array of strings" };
  }
  return {
    ok: true,
    value: { id: record.id, name: record.name, roles: record.roles },
  };
}

function report(text: string): string {
  const result = parseUser(text);
  // The union is discriminated, so the success branch has a User and the
  // failure branch has no `value` to reach for.
  return result.ok
    ? result.value.name + " has " + result.value.roles.length + " role(s)"
    : "rejected: " + result.error;
}

console.log(report('{"id":"u-1","name":"ada","roles":["admin"]}'));
console.log(report('{"id":7,"name":"ada","roles":[]}'));
console.log(report('{"id":"u-1","name":"ada"}'));
console.log(report('{"id":"u-1","name":"ada","roles":["admin",2]}'));
console.log(report("not json at all"));
