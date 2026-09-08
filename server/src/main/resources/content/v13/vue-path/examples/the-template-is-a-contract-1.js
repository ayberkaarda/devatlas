// A template is a contract: given this state, the output is this.
//
// Vue 3.5 compiles a template into a render function before any of it runs.
// This models that compile step with a mustache compiler so the contract can be
// inspected in plain JavaScript. It is not Vue's compiler -- it handles nothing
// but text interpolation -- but the property it demonstrates is Vue's: the
// template is compiled once, and rendering is a pure function of state.

function compile(template) {
  // Odd positions are the captured expressions; even positions are static text.
  const parts = template.split(/\{\{(.+?)\}\}/gs);
  const statics = parts.filter((_, index) => index % 2 === 0);
  const expressions = parts
    .filter((_, index) => index % 2 === 1)
    .map((source) => source.trim());

  const evaluators = expressions.map((source) => ({
    source,
    run: (scope) =>
      new Function(...Object.keys(scope), `return (${source})`)(
        ...Object.values(scope),
      ),
  }));

  const render = (scope) => {
    let out = statics[0];
    for (let i = 0; i < evaluators.length; i++) {
      out += String(evaluators[i].run(scope)) + statics[i + 1];
    }
    return out;
  };

  render.statics = statics.length;
  render.expressions = expressions.map((e) => e);
  return render;
}

const template = "Hello, {{ user.name }} - {{ items.length }} items";
const render = compile(template);

const first = { user: { name: "Ada" }, items: [1, 2, 3] };
const second = { user: { name: "Grace" }, items: [1, 2, 3, 4] };

console.log(`render 1: ${render(first)}`);
console.log(`render 2: ${render(first)}`);
console.log(`same state, same output: ${render(first) === render(first)}`);
console.log(`after new state: ${render(second)}`);
console.log(`compiled once, rendered many: ${typeof render === "function"}`);
console.log(`static chunks: ${render.statics}`);
console.log(`expressions the compiler found: ${render.expressions.join(" | ")}`);
