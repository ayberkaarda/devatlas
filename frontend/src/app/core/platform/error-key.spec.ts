import en from '../../../assets/i18n/en.json';
import { PlatformError } from './errors';
import { errorKey, queueErrorKey, TRANSLATED_CODES } from './error-key';

describe('error-key', () => {
  it('has a non-empty en.json translation for every code it claims to translate', () => {
    const translated: Record<string, string> = en.error;

    for (const code of TRANSLATED_CODES) {
      expect(translated[code]).toBeDefined();
      expect(translated[code]?.trim().length).toBeGreaterThan(0);
    }
  });

  it('falls back to the generic key for a code the catalogue does not cover', () => {
    const error = new PlatformError('SOME_CODE_NOBODY_REGISTERED', 'irrelevant');

    expect(errorKey(error)).toBe('error.INTERNAL_ERROR');
  });

  it('reports no error for a queue entry that never failed', () => {
    expect(queueErrorKey(null)).toBeNull();
  });
});
