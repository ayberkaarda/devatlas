import { TestBed } from '@angular/core/testing';

import { MarkdownService } from './markdown.service';

describe('MarkdownService', () => {
  let markdown: MarkdownService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    markdown = TestBed.inject(MarkdownService);
  });

  it('renders ordinary markdown', async () => {
    const html = await markdown.render('## Signals\n\nA **signal** wraps a value.\n', 'light');
    expect(html).toContain('<h3');
    expect(html).toContain('<strong>signal</strong>');
  });

  it('nests body headings under the heading of the page they are rendered into', async () => {
    const html = await markdown.render('# Title\n\n## Section\n', 'light');

    // A body that starts with `# Title` would otherwise put a second
    // first-level heading on a screen that already has one, and a document
    // with two of them has no single answer to what it is about.
    expect(html).not.toContain('<h1');
    expect(html).toContain('<h2>Title</h2>');
    expect(html).toContain('<h3>Section</h3>');
  });

  it('stops shifting at the deepest heading level rather than inventing an h7', async () => {
    const html = await markdown.render('###### Deep\n', 'light');
    expect(html).toContain('<h6>Deep</h6>');
  });

  it('removes a script tag from the rendered markup', async () => {
    const html = await markdown.render(
      'Before\n\n<script>window.stolen = document.cookie;</script>\n\nAfter\n',
      'light',
    );

    expect(html).not.toContain('<script');
    expect(html).not.toContain('document.cookie');
    // The surrounding prose survives: sanitization removes the dangerous node,
    // it does not discard the document.
    expect(html).toContain('Before');
    expect(html).toContain('After');
  });

  it('removes an inline event handler while keeping the element', async () => {
    const html = await markdown.render(
      '<img src="x" onerror="window.stolen = 1" alt="broken">\n',
      'light',
    );

    expect(html).not.toContain('onerror');
    expect(html).not.toContain('window.stolen');
  });

  it('strips a javascript: link target', async () => {
    const html = await markdown.render('[click](javascript:alert(1))\n', 'light');
    expect(html).not.toContain('javascript:');
  });

  it('escapes code example source rather than letting it reach the DOM as markup', async () => {
    const html = await markdown.highlight('<script>alert(1)</script>', 'text', 'light');
    expect(html).not.toContain('<script>alert(1)</script>');
    expect(html).toContain('&lt;script&gt;');
  });

  it('renders a fenced block as a code block even with no highlighter available', async () => {
    const html = await markdown.render('```ts\nconst c = signal(0);\n```\n', 'dark');
    expect(html).toContain('<pre');
    expect(html).toContain('signal(0)');
    expect(html).not.toContain('<script');
  });

  describe('fenced blocks that quote tool output', () => {
    it('leaves a block tagged with a real language unwrapped', async () => {
      const html = await markdown.render('```rust\nlet total = 1 + 1;\n```\n', 'light');

      expect(html).not.toContain('lesson-output');
      expect(html).toContain('<pre');
      expect(html).toContain('let total = 1 + 1;');
    });

    it('wraps a block with no language tag', async () => {
      const html = await markdown.render(
        '```\nerror[E0382]: borrow of moved value\n```\n',
        'light',
      );

      expect(html).toContain('<div class="lesson-output">');
      expect(html).toContain('error[E0382]: borrow of moved value');
    });

    it('wraps a block tagged text', async () => {
      const html = await markdown.render('```text\nthread panicked at src/main.rs\n```\n', 'light');

      expect(html).toContain('<div class="lesson-output">');
      expect(html).toContain('thread panicked at src/main.rs');
    });

    it('wraps a tag that differs only by surrounding space or case', async () => {
      const html = await markdown.render('```  TEXT  \nrecorded output\n```\n', 'light');
      expect(html).toContain('<div class="lesson-output">');
    });

    it('leaves a tag that merely contains text alone', async () => {
      // `plaintext` names a grammar the highlighter knows, so a substring test
      // here would take a real language for a transcript.
      const html = await markdown.render('```plaintext\nsome configured value\n```\n', 'light');

      expect(html).not.toContain('lesson-output');
      expect(html).toContain('some configured value');
    });

    it('keeps the wrapper through sanitization', async () => {
      // The assertion is on what render returns, which is the sanitized
      // string: what a stylesheet can reach is only what survived here.
      const html = await markdown.render('```\nexit status 1\n```\n', 'light');

      expect(html).toMatch(/<div class="lesson-output">\s*<pre/);
      expect(html).toContain('</div>');
    });

    it('distinguishes two blocks whose text is identical but whose tags differ', async () => {
      const source = 'value: 3\n';
      const html = await markdown.render(
        `\`\`\`yaml\n${source}\`\`\`\n\n\`\`\`text\n${source}\`\`\`\n`,
        'light',
      );

      const wrappers = html.match(/<div class="lesson-output">/g) ?? [];
      expect(wrappers).toHaveLength(1);
      // Both blocks are still rendered; only one of them is marked.
      expect(html.match(/<pre/g) ?? []).toHaveLength(2);
    });
  });
});
