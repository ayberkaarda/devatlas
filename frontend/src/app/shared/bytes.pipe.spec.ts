import { BytesFormatPipe } from './bytes.pipe';

describe('BytesFormatPipe', () => {
  const pipe = new BytesFormatPipe();

  it('renders sub-kilobyte values in bytes', () => {
    expect(pipe.transform(512)).toBe('512 B');
  });

  it("matches the content-sync protocol's own worked example", () => {
    // docs/protocol/content-sync.md §4.1: "about 1.8 MB" for this exact size.
    expect(pipe.transform(1893441)).toBe('1.8 MB');
  });

  it('renders kilobytes', () => {
    expect(pipe.transform(20480)).toBe('20.0 KB');
  });

  it('renders gigabytes', () => {
    expect(pipe.transform(2 * 1024 * 1024 * 1024)).toBe('2.0 GB');
  });

  it('treats a negative or non-finite value as zero', () => {
    expect(pipe.transform(-5)).toBe('0 B');
    expect(pipe.transform(Number.NaN)).toBe('0 B');
  });
});
