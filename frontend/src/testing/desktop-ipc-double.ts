/**
 * A stand-in for the desktop runtime's inter-process bridge, used only by
 * tests.
 *
 * The desktop implementation is the one file allowed to import the real
 * bridge; a test cannot load that module because it is published as an ES
 * module with no runtime behind it outside a desktop window. The test runner
 * therefore resolves the bridge to this file instead, which lets the real
 * implementation be exercised unchanged — its mapping, its error translation
 * and its identifier resolution are what the tests are actually about.
 *
 * Nothing here imports a desktop package, and nothing here runs in a build.
 */

type Handler = (command: string, args?: Record<string, unknown>) => Promise<unknown>;
type EventHandler = (event: { payload: unknown }) => void;

const rejectEverything: Handler = (command) =>
  Promise.reject(new Error(`No test handler is installed for the command '${command}'.`));

let handler: Handler = rejectEverything;
const listeners = new Map<string, Set<EventHandler>>();

/** Records every command the code under test issued, in order. */
export const calls: { command: string; args?: Record<string, unknown> }[] = [];

/** Installs the responder for the next set of assertions. */
export function respondWith(next: Handler): void {
  handler = next;
}

/** Clears handlers, listeners and the call log between tests. */
export function resetDesktopDouble(): void {
  handler = rejectEverything;
  listeners.clear();
  calls.length = 0;
}

/** Pushes an event to whatever subscribed to the given name. */
export function emitDesktopEvent(name: string, payload: unknown): void {
  for (const listener of listeners.get(name) ?? []) {
    listener({ payload });
  }
}

/** How many subscriptions are currently open for a name. */
export function listenerCount(name: string): number {
  return listeners.get(name)?.size ?? 0;
}

export const invoke = async <T>(command: string, args?: Record<string, unknown>): Promise<T> => {
  calls.push({ command, args });
  return (await handler(command, args)) as T;
};

export const listen = async <T>(
  name: string,
  callback: (event: { payload: T }) => void,
): Promise<() => void> => {
  const typed = callback as EventHandler;
  const bucket = listeners.get(name) ?? new Set<EventHandler>();
  bucket.add(typed);
  listeners.set(name, bucket);
  return () => {
    bucket.delete(typed);
  };
};
