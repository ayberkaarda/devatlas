// Structural typing: what a value can do is what decides which types accept it.
interface Pet {
  name: string;
}

class Dog {
  constructor(
    public name: string,
    public breed: string,
  ) {}
}

function greet(pet: Pet): string {
  return "Hello, " + pet.name;
}

const rex = new Dog("Rex", "collie");
const anonymous = { name: "Mochi", legs: 4 };

// Neither Dog nor the object literal mentions Pet anywhere.
console.log(greet(rex));
console.log(greet(anonymous));

const pet: Pet = rex;
console.log(pet.name);
