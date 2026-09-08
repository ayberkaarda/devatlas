interface User {
  id: string;
  name: string;
  roles: string[];
}

// `as` is a claim about a value. Nothing verifies it, and nothing is emitted.
const user = JSON.parse('{"id":7,"name":"ada"}') as User;

console.log(typeof user.id, user.id);
console.log(user.roles);

try {
  console.log(user.id.toUpperCase());
} catch (error) {
  console.log("runtime failure:", (error as Error).message);
}

try {
  console.log(user.roles.length);
} catch (error) {
  console.log("runtime failure:", (error as Error).message);
}
