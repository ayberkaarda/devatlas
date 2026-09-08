// A value that does not announce its own changes, and the two ways that goes
// wrong. No framework here on purpose: this is the problem a signal solves,
// written out, so that the solution has something to be measured against.

// 1. A derived value read once is a copy, and a copy goes stale in silence.
const items: string[] = ['manifest'];
const copiedCount = items.length;
items.push('lesson');

console.log(`copied: ${copiedCount}`);
console.log(`actual: ${items.length}`);

// 2. Announcing changes by hand works, and works only for the writes that
// remember to announce. `addQuietly` is not exotic: it is what every second
// mutation looks like once a class has more than one way to change.
type Listener = () => void;

class ObservedQueue {
  private readonly entries: string[] = [];
  private readonly listeners: Listener[] = [];

  onChange(listener: Listener): void {
    this.listeners.push(listener);
  }

  add(entry: string): void {
    this.entries.push(entry);
    for (const listener of this.listeners) {
      listener();
    }
  }

  addQuietly(entry: string): void {
    this.entries.push(entry);
  }

  get size(): number {
    return this.entries.length;
  }
}

const queue = new ObservedQueue();
let notifications = 0;
queue.onChange(() => {
  notifications++;
});

queue.add('first');
queue.addQuietly('second');

console.log(`entries: ${queue.size}`);
console.log(`notifications: ${notifications}`);
