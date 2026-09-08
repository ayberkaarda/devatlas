// What the two ways of putting a string on the page actually do.
//
// Vue 3.5's template syntax documentation says the double mustaches "interpret
// the data as plain text, not HTML", and that `v-html` outputs real HTML and
// must never be used on user-provided content. The difference is one escaping
// step, and it is small enough to model exactly.

const ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };

function escapeHtml(value) {
  return String(value).replace(/[&<>"']/g, (character) => ESCAPES[character]);
}

// `{{ expression }}` -- the value is escaped on its way into the document.
function interpolate(value) {
  return `<p>${escapeHtml(value)}</p>`;
}

// `v-html="expression"` -- the value becomes markup.
function rawHtml(value) {
  return `<p>${String(value)}</p>`;
}

const trusted = "Vue 3.5 & the template";
const fromAUser = '<img src=x onerror="steal()">';

console.log(`interpolated, trusted:  ${interpolate(trusted)}`);
console.log(`interpolated, hostile:  ${interpolate(fromAUser)}`);
console.log(`v-html, hostile:        ${rawHtml(fromAUser)}`);

const tagPattern = /<img/i;
console.log(
  `interpolation produced a tag: ${tagPattern.test(interpolate(fromAUser))}`,
);
console.log(`v-html produced a tag: ${tagPattern.test(rawHtml(fromAUser))}`);
console.log(
  `an ampersand survives interpolation as text: ${interpolate("a & b").includes("&amp;")}`,
);
