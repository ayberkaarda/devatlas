// A type alias introduces a name, not a new type.
type Metres = number;
type Seconds = number;

function speed(distance: Metres, time: Seconds): number {
  return distance / time;
}

console.log(speed(100, 9.58));
console.log(speed(9.58, 100)); // Same types, arguments swapped, no diagnostic.

// A brand adds a member that no runtime value ever carries, which is enough to
// make two number types structurally different.
type Branded<T, B extends string> = T & { readonly __brand: B };
type BrandedMetres = Branded<number, "metres">;
type BrandedSeconds = Branded<number, "seconds">;

const distance = 100 as BrandedMetres;
const time = 9.58 as BrandedSeconds;

function brandedSpeed(d: BrandedMetres, t: BrandedSeconds): number {
  return d / t;
}

console.log(brandedSpeed(distance, time));
// @ts-expect-error Argument of type 'BrandedSeconds' is not assignable to parameter of type 'BrandedMetres'.
console.log(brandedSpeed(time, distance));

// The brand is erased: at runtime this is an ordinary number.
console.log(typeof distance);
