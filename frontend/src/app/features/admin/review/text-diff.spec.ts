import { diffWords, exceedsDiffLimit } from './text-diff';

describe('diffWords', () => {
  it('returns a single equal segment for identical text', () => {
    expect(diffWords('Spring Boot 4.1.1 is available.', 'Spring Boot 4.1.1 is available.')).toEqual(
      [{ kind: 'equal', text: 'Spring Boot 4.1.1 is available.' }],
    );
  });

  it('returns nothing for two empty strings', () => {
    expect(diffWords('', '')).toEqual([]);
  });

  it('marks a pure addition and a pure removal', () => {
    expect(diffWords('', 'hello')).toEqual([{ kind: 'added', text: 'hello' }]);
    expect(diffWords('hello', '')).toEqual([{ kind: 'removed', text: 'hello' }]);
  });

  it('keeps the words that did not change, rather than calling the whole line different', () => {
    // A line-based diff over these two single-line inputs would report the
    // entire line as changed. The word-based diff this module implements
    // must keep the unchanged words as their own 'equal' segments instead.
    const before = 'Spring Boot 4.1.0 is released.';
    const after = 'Spring Boot 4.1.1 is available.';

    const segments = diffWords(before, after);
    const equalText = segments
      .filter((segment) => segment.kind === 'equal')
      .map((segment) => segment.text)
      .join('');

    expect(equalText).toContain('Spring');
    expect(equalText).toContain('Boot');
    expect(equalText).toContain('is');
    expect(segments.some((s) => s.kind === 'removed' && s.text.includes('4.1.0'))).toBe(true);
    expect(segments.some((s) => s.kind === 'removed' && s.text.includes('released'))).toBe(true);
    expect(segments.some((s) => s.kind === 'added' && s.text.includes('4.1.1'))).toBe(true);
    expect(segments.some((s) => s.kind === 'added' && s.text.includes('available'))).toBe(true);
  });

  it('reconstructs both inputs exactly from the segments it returns', () => {
    const before = 'the quick brown fox jumps over the lazy dog';
    const after = 'the quick red fox jumps over the sleepy dog';

    const segments = diffWords(before, after);
    const reconstructedBefore = segments
      .filter((s) => s.kind === 'equal' || s.kind === 'removed')
      .map((s) => s.text)
      .join('');
    const reconstructedAfter = segments
      .filter((s) => s.kind === 'equal' || s.kind === 'added')
      .map((s) => s.text)
      .join('');

    expect(reconstructedBefore).toBe(before);
    expect(reconstructedAfter).toBe(after);
  });

  it('is deterministic: the same pair of inputs always segments the same way', () => {
    const before = 'the quick brown fox jumps over the lazy dog';
    const after = 'the quick red fox jumps over the sleepy dog';

    const first = diffWords(before, after);
    const second = diffWords(before, after);

    expect(second).toEqual(first);
  });

  it('handles a large identical pair without building an LCS table at all', () => {
    const huge = Array.from({ length: 50_000 }, (_, i) => `word${i}`).join(' ');
    // Equality is checked before tokenizing, so this must return instantly
    // regardless of size; a timing-out test here would mean the fast path
    // was lost.
    expect(diffWords(huge, huge)).toEqual([{ kind: 'equal', text: huge }]);
  });

  it('falls back to a wholesale removed/added pair once the LCS table would be too large', () => {
    const before = Array.from({ length: 3000 }, (_, i) => `before-${i}`).join(' ');
    const after = Array.from({ length: 3000 }, (_, i) => `after-${i}`).join(' ');

    expect(exceedsDiffLimit(before, after)).toBe(true);
    expect(diffWords(before, after)).toEqual([
      { kind: 'removed', text: before },
      { kind: 'added', text: after },
    ]);
  });

  it('stays under the limit for an ordinary-sized post, so the fallback never fires by accident', () => {
    const before = Array.from({ length: 500 }, (_, i) => `word${i}`).join(' ');
    const after = before.replace('word10 ', 'changed10 ');

    expect(exceedsDiffLimit(before, after)).toBe(false);
    const segments = diffWords(before, after);
    expect(segments.some((s) => s.kind === 'removed')).toBe(true);
    expect(segments.some((s) => s.kind === 'added')).toBe(true);
  });
});
