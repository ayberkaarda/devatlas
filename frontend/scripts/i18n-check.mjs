#!/usr/bin/env node
/*
 * Locale parity gate.
 *
 * Every key must exist in all four locale files with a non-empty value. This
 * is checked mechanically rather than by review because the failure it catches
 * is invisible: a missing key renders as the key itself, which looks like a
 * layout bug rather than a translation bug and is easy to attribute to
 * something else entirely. An empty string is worse — it renders as nothing at
 * all, so a button loses its label and the page still looks finished.
 *
 * The script also refuses a key that exists only in one file. An extra key is
 * not harmless: it is either a typo of a real key, in which case the real one
 * is missing somewhere, or it is dead weight that will be translated again at
 * the next pass.
 *
 * Exits non-zero on any problem, so it can gate a build.
 */
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, relative } from 'node:path';

const LOCALES = ['en', 'tr', 'fr', 'de'];
const REFERENCE = 'en';

const here = dirname(fileURLToPath(import.meta.url));
const projectRoot = join(here, '..');
const localeDir = join(projectRoot, 'src', 'assets', 'i18n');

/** Flattens nested objects into dotted keys, the form the templates use. */
function flatten(value, prefix, into) {
  for (const [key, entry] of Object.entries(value)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (entry !== null && typeof entry === 'object' && !Array.isArray(entry)) {
      flatten(entry, path, into);
    } else {
      into.set(path, entry);
    }
  }
  return into;
}

function load(locale) {
  const file = join(localeDir, `${locale}.json`);
  let raw;
  try {
    raw = readFileSync(file, 'utf8');
  } catch (error) {
    return { file, error: `cannot be read (${error.code ?? error.message})` };
  }
  try {
    return { file, keys: flatten(JSON.parse(raw), '', new Map()) };
  } catch (error) {
    return { file, error: `is not valid JSON (${error.message})` };
  }
}

const loaded = new Map(LOCALES.map((locale) => [locale, load(locale)]));
const problems = [];

for (const [locale, result] of loaded) {
  if (result.error) {
    problems.push(`${locale}: ${relative(projectRoot, result.file)} ${result.error}`);
  }
}

if (problems.length === 0) {
  // The union rather than the reference alone, so a key added to tr but never
  // to en is reported against en instead of silently accepted.
  const union = new Set();
  for (const { keys } of loaded.values()) {
    for (const key of keys.keys()) {
      union.add(key);
    }
  }

  for (const key of [...union].sort()) {
    for (const locale of LOCALES) {
      const keys = loaded.get(locale).keys;
      if (!keys.has(key)) {
        problems.push(`${locale}: missing key "${key}"`);
        continue;
      }
      const value = keys.get(key);
      if (typeof value !== 'string') {
        problems.push(
          `${locale}: key "${key}" is ${value === null ? 'null' : typeof value}, expected a string`,
        );
      } else if (value.trim() === '') {
        problems.push(`${locale}: key "${key}" is empty`);
      }
    }
  }

  const referenceCount = loaded.get(REFERENCE).keys.size;
  if (problems.length === 0) {
    console.log(
      `i18n: ${referenceCount} keys, identical across ${LOCALES.join(', ')}, no empty values.`,
    );
    process.exit(0);
  }
}

console.error(`i18n: ${problems.length} problem(s) found.`);
for (const problem of problems) {
  console.error(`  - ${problem}`);
}
process.exit(1);
