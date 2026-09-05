import { Pipe, PipeTransform } from '@angular/core';

const UNITS: readonly string[] = ['B', 'KB', 'MB', 'GB'];

/**
 * A human-readable size, e.g. `1893441` → `"1.8 MB"`.
 *
 * Binary steps (1024), matching the figure the content-sync protocol itself
 * uses for the same example size — "about 1.8 MB" for 1,893,441 bytes — so a
 * number shown here reads the same way the protocol document already does.
 */
@Pipe({ name: 'bytesFormat' })
export class BytesFormatPipe implements PipeTransform {
  transform(value: number): string {
    if (!Number.isFinite(value) || value < 0) {
      return '0 B';
    }
    if (value < 1024) {
      return `${Math.round(value)} B`;
    }
    let scaled = value;
    let unitIndex = 0;
    while (scaled >= 1024 && unitIndex < UNITS.length - 1) {
      scaled /= 1024;
      unitIndex += 1;
    }
    return `${scaled.toFixed(1)} ${UNITS[unitIndex]}`;
  }
}
