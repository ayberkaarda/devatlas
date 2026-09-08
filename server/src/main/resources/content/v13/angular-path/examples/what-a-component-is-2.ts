import { Component } from '@angular/core';

/**
 * The metadata object is typed, and a misspelled key is a compile error rather
 * than a setting that quietly does nothing.
 *
 * `styleUrls` (plural) was the property name before Angular 15; a single
 * `styleUrl` was added alongside it and is what the command line tool puts in a
 * new component in Angular 22. Writing the plural where the singular belongs is
 * the mistake this listing preserves, with the expected error marked so that the
 * file still compiles.
 */
@Component({
  selector: 'app-typo',
  template: `<p>The decorator is a typed object, not a bag of settings.</p>`,
  // @ts-expect-error 'templateUrls' does not exist in type 'Component'.
  templateUrls: ['./typo.html'],
})
export class Typo {}

/**
 * The same component written correctly. `template` and `templateUrl` are
 * alternatives: a component carries one of them, never both.
 */
@Component({
  selector: 'app-correct',
  template: `<p>The decorator is a typed object, not a bag of settings.</p>`,
  styles: `
    p {
      margin: 0;
    }
  `,
})
export class Correct {}
