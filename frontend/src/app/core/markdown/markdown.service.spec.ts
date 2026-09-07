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
});
