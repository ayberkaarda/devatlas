#!/usr/bin/env node
/*
 * Locale parity and usage gate.
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
 * Parity alone misses a whole class of failure: a key that is used by a
 * template or component but was never added to any locale file at all. In
 * that case every locale agrees with every other locale — there is nothing to
 * compare against — and the check above stays green while the screen renders
 * the raw dotted key instead of real text. To catch that, this script also
 * scans every .ts and .html file under src/app for translation keys actually
 * referenced, in two shapes:
 *
 *   - a literal key piped directly through translate, including both
 *     branches of a parenthesized ternary right before the pipe, e.g.
 *     `'a.b' | translate` or `(cond ? 'a.b' : 'a.c') | translate`;
 *   - a literal prefix concatenated with a runtime value before the same
 *     pipe, e.g. `'download.state.' + entry.state | translate`. A prefix
 *     match marks every catalog key under that prefix as referenced, because
 *     the concrete key is only known at runtime, not from the source text.
 *   - the same kind of literal prefix opening a template literal, e.g.
 *     `` `error.${code}` ``. This shape never appears next to `translate` at
 *     all — the function that builds it hands the finished string to a caller
 *     in another file, which is the one that pipes it — so it is recognised
 *     on its own rather than as a variant of the piped patterns above.
 *
 * A key used this way but absent from the catalog is a hard failure, same as
 * a missing-parity key. The reverse — a catalog key this scan never found a
 * reference to — is reported as a warning only, never a failure: a key can
 * also be assembled entirely in TypeScript and handed to the pipe as a plain
 * variable (an interpolated language code, a difficulty level, a theme name),
 * which a static text scan has no way to trace back to the literal pieces
 * that built it. That shape is expected, not a defect.
 *
 * Exits non-zero on any problem, so it can gate a build. Usage warnings never
 * change the exit code.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, extname, join, relative } from 'node:path';

const LOCALES = ['en', 'tr', 'fr', 'de'];
const REFERENCE = 'en';

const here = dirname(fileURLToPath(import.meta.url));
const projectRoot = join(here, '..');
const localeDir = join(projectRoot, 'src', 'assets', 'i18n');
const appDir = join(projectRoot, 'src', 'app');

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

/** Recursively lists every .ts and .html file under `dir`. */
function listSourceFiles(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const stats = statSync(full);
    if (stats.isDirectory()) {
      out.push(...listSourceFiles(full));
    } else if (extname(entry) === '.ts' || extname(entry) === '.html') {
      out.push(full);
    }
  }
  return out;
}

// A catalog key is always two or more dot-separated identifier segments —
// every real key in the locale files is nested at least one level deep.
const KEY_BODY = String.raw`[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+`;
const STATIC_KEY_RE = new RegExp(`['"](${KEY_BODY})['"]\\s*\\|\\s*translate\\b`, 'g');
const LITERAL_RE = new RegExp(`['"](${KEY_BODY})['"]`, 'g');
const PREFIX_RE = new RegExp(
  String.raw`['"]([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)*\.)['"]\s*\+\s*[^\n]*?\|\s*translate\b`,
  'g',
);
// A backtick template literal whose fixed opening segment is followed by an
// interpolation, e.g. `error.${code}`. Written as a plain string rather than
// String.raw so the backtick delimiting this pattern itself needs no escape.
const TEMPLATE_PREFIX_RE = new RegExp(
  "`([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*\\.)\\$\\{",
  'g',
);
const CLOSE_BEFORE_TRANSLATE_RE = /\)\s*\|\s*translate\b/g;

/** Finds the index of the '(' that matches the ')' found at `closeIndex`. */
function matchOpenParen(text, closeIndex) {
  let depth = 1;
  for (let i = closeIndex - 1; i >= 0; i -= 1) {
    if (text[i] === ')') {
      depth += 1;
    } else if (text[i] === '(') {
      depth -= 1;
      if (depth === 0) {
        return i;
      }
    }
  }
  return -1;
}

/** Records every translation key statically referenced in one source file. */
function scanUsage(text, staticKeys, prefixes) {
  for (const match of text.matchAll(STATIC_KEY_RE)) {
    staticKeys.add(match[1]);
  }
  for (const match of text.matchAll(PREFIX_RE)) {
    prefixes.add(match[1]);
  }
  for (const match of text.matchAll(TEMPLATE_PREFIX_RE)) {
    prefixes.add(match[1]);
  }
  for (const match of text.matchAll(CLOSE_BEFORE_TRANSLATE_RE)) {
    const openIndex = matchOpenParen(text, match.index);
    if (openIndex === -1) {
      continue;
    }
    const inner = text.slice(openIndex + 1, match.index);
    for (const literal of inner.matchAll(LITERAL_RE)) {
      staticKeys.add(literal[1]);
    }
  }
}

const loaded = new Map(LOCALES.map((locale) => [locale, load(locale)]));
const problems = [];
const warnings = [];
let referenceCount = 0;

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

  referenceCount = loaded.get(REFERENCE).keys.size;

  if (problems.length === 0) {
    const catalog = new Set(loaded.get(REFERENCE).keys.keys());
    const staticKeys = new Set();
    const prefixes = new Set();

    for (const file of listSourceFiles(appDir)) {
      scanUsage(readFileSync(file, 'utf8'), staticKeys, prefixes);
    }

    for (const key of [...staticKeys].sort()) {
      if (!catalog.has(key)) {
        problems.push(`used in src/app but not defined in any locale: "${key}"`);
      }
    }

    if (problems.length === 0) {
      const prefixList = [...prefixes];
      for (const key of [...catalog].sort()) {
        const referenced = staticKeys.has(key) || prefixList.some((prefix) => key.startsWith(prefix));
        if (!referenced) {
          warnings.push(key);
        }
      }
    }
  }
}

if (problems.length === 0) {
  console.log(
    `i18n: ${referenceCount} keys, identical across ${LOCALES.join(', ')}, no empty values.`,
  );
  if (warnings.length > 0) {
    console.log(
      `i18n: ${warnings.length} key(s) defined but not found by the static usage scan ` +
        '(expected when a key is assembled from a runtime value in TypeScript — e.g. ' +
        '"language." + code, "lesson.difficulty." + level, a theme name — this is not a failure):',
    );
    for (const key of warnings) {
      console.log(`  - ${key}`);
    }
  }
  process.exit(0);
}

console.error(`i18n: ${problems.length} problem(s) found.`);
for (const problem of problems) {
  console.error(`  - ${problem}`);
}
process.exit(1);
