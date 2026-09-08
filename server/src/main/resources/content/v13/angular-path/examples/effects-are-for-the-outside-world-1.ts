// Why an effect gets a cleanup callback: a request that is already in flight
// when the next one starts, and the wrong answer that arrives last.
//
// No framework here. The race is ordinary asynchronous JavaScript, and seeing
// it without Angular is what makes `onCleanup` look like a fix rather than
// ceremony.

function load(slug: string, afterMs: number): Promise<string> {
  return new Promise((resolve) => {
    setTimeout(() => resolve(`body of ${slug}`), afterMs);
  });
}

async function withoutCleanup(): Promise<string> {
  let shown = 'nothing';

  // The reader opens a slow post and immediately navigates to a fast one.
  const slow = load('what-a-component-is', 40).then((body) => {
    shown = body;
  });
  const fast = load('signals-and-reactivity', 5).then((body) => {
    shown = body;
  });

  await Promise.all([slow, fast]);
  return shown;
}

async function withCleanup(): Promise<string> {
  let shown = 'nothing';
  let generation = 0;

  function start(slug: string, afterMs: number): Promise<void> {
    // Incrementing the generation is what a cleanup callback does for you: it
    // marks everything already in flight as no longer wanted.
    generation++;
    const mine = generation;
    return load(slug, afterMs).then((body) => {
      if (mine === generation) {
        shown = body;
      }
    });
  }

  const slow = start('what-a-component-is', 40);
  const fast = start('signals-and-reactivity', 5);

  await Promise.all([slow, fast]);
  return shown;
}

async function main(): Promise<void> {
  console.log(`requested last: signals-and-reactivity`);
  console.log(`without cleanup: ${await withoutCleanup()}`);
  console.log(`with cleanup: ${await withCleanup()}`);
}

void main();
