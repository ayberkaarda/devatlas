// "Absent" and "empty" are two different facts, and a truthiness test cannot
// tell them apart. This is the shape of the bug that a route-bound optional
// input invites, isolated from the framework so the difference can be printed.

type Mode = 'create' | 'edit' | 'malformed';

/** Wrong: every falsy value is treated as "the segment was not there". */
function looseMode(id: string | undefined): Mode {
  return id ? 'edit' : 'create';
}

/** Right: absence is compared with `undefined`; an empty segment is refused. */
function strictMode(id: string | undefined): Mode {
  if (id === undefined) {
    return 'create';
  }
  return id.trim() === '' ? 'malformed' : 'edit';
}

const cases: readonly (string | undefined)[] = [undefined, '019205a0', '', '   '];

for (const id of cases) {
  const shown = id === undefined ? 'undefined' : `'${id}'`;
  console.log(`${shown} -> loose: ${looseMode(id)}, strict: ${strictMode(id)}`);
}
