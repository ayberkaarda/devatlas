// Why an assertion names an element instead of quoting a sentence.
//
// Two ways of finding the same thing in the same rendered markup, run against
// the markup before and after a wording change that no reviewer would think of
// as a behaviour change.

function hasTestId(html: string, testId: string): boolean {
  return html.includes(`data-testid="${testId}"`);
}

function hasText(html: string, text: string): boolean {
  return html.includes(text);
}

const before = `
  <section data-testid="download-panel">
    <h2>Download progress</h2>
    <button data-testid="download-start" type="button">Start download</button>
  </section>
`;

// The same component after the interface strings were revised — or after the
// page was rendered in another language, which is the same event as far as an
// assertion over visible text is concerned.
const after = `
  <section data-testid="download-panel">
    <h2>Downloading</h2>
    <button data-testid="download-start" type="button">Start</button>
  </section>
`;

for (const [label, html] of [
  ['before', before],
  ['after', after],
] as const) {
  console.log(
    `${label}: by test id ${hasTestId(html, 'download-start')}, by text ${hasText(html, 'Start download')}`,
  );
}
