// Memoised derivation written by hand, so that what `computed` does for free
// has a price tag attached. No framework here; the point is the bookkeeping.

interface Lesson {
  readonly slug: string;
  readonly minutes: number;
}

let derivations = 0;

function totalMinutes(lessons: readonly Lesson[]): number {
  derivations++;
  return lessons.reduce((sum, lesson) => sum + lesson.minutes, 0);
}

// A cache keyed on the input reference. This is the whole trick, and it is also
// the whole hazard: the key has to be exactly what the derivation depends on.
let cachedFor: readonly Lesson[] | null = null;
let cachedValue = 0;

function memoisedTotal(lessons: readonly Lesson[]): number {
  if (cachedFor !== lessons) {
    cachedValue = totalMinutes(lessons);
    cachedFor = lessons;
  }
  return cachedValue;
}

const first: readonly Lesson[] = [
  { slug: 'what-a-component-is', minutes: 12 },
  { slug: 'signals-and-reactivity', minutes: 14 },
];

console.log(`total: ${memoisedTotal(first)}`);
console.log(`total again: ${memoisedTotal(first)}`);
console.log(`derivations so far: ${derivations}`);

const second: readonly Lesson[] = [...first, { slug: 'effects', minutes: 9 }];

console.log(`total after a change: ${memoisedTotal(second)}`);
console.log(`derivations so far: ${derivations}`);

// The hazard, made concrete. A second input the cache key does not mention is
// an input the cache cannot see changing, and the answer that comes back is
// wrong rather than merely slow.
let multiplier = 1;
let scaledCachedFor: readonly Lesson[] | null = null;
let scaledCached = 0;

function memoisedScaled(lessons: readonly Lesson[]): number {
  if (scaledCachedFor !== lessons) {
    scaledCached = totalMinutes(lessons) * multiplier;
    scaledCachedFor = lessons;
  }
  return scaledCached;
}

console.log(`scaled: ${memoisedScaled(second)}`);
multiplier = 2;
console.log(`scaled after the multiplier changed: ${memoisedScaled(second)}`);
console.log(`the honest answer: ${totalMinutes(second) * multiplier}`);
