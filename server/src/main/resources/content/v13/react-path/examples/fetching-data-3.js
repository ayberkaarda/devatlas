// The same screen written for React 19. Not executed: JSX is not valid input to
// a plain Node run and this repository does not install React, so there is no
// recorded output for this file.
//
// Two shapes appear here. The effect version owns the request and cancels it,
// which is the shape a component reaches for when it fetches on the client. The
// `use` version reads a promise created outside render and suspends until it
// resolves; React 19 warns if the promise is created during render, because a
// new promise on every render can never settle the component.

import { Suspense, use, useEffect, useState } from "react";

function SearchResults({ query }) {
  const [state, setState] = useState({ status: "idle" });

  useEffect(() => {
    if (query === "") {
      setState({ status: "idle" });
      return;
    }
    const controller = new AbortController();
    let ignore = false;
    setState({ status: "loading" });

    fetch(`/api/search?q=${encodeURIComponent(query)}`, { signal: controller.signal })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`search failed with ${response.status}`);
        }
        return response.json();
      })
      .then((results) => {
        if (!ignore) {
          setState({ status: "success", results });
        }
      })
      .catch((error) => {
        if (!ignore && error.name !== "AbortError") {
          setState({ status: "error", message: error.message });
        }
      });

    return () => {
      ignore = true;
      controller.abort();
    };
  }, [query]);

  switch (state.status) {
    case "idle":
      return <p>Type to search.</p>;
    case "loading":
      return <p>Searching…</p>;
    case "error":
      return <p role="alert">{state.message}</p>;
    case "success":
      return (
        <ul>
          {state.results.map((result) => (
            <li key={result.id}>{result.title}</li>
          ))}
        </ul>
      );
    default:
      return null;
  }
}

// The promise is created by whatever owns the query, not during this render.
function Results({ resultsPromise }) {
  const results = use(resultsPromise);
  return (
    <ul>
      {results.map((result) => (
        <li key={result.id}>{result.title}</li>
      ))}
    </ul>
  );
}

export default function SearchPage({ query, resultsPromise }) {
  return (
    <>
      <SearchResults query={query} />
      <Suspense fallback={<p>Searching…</p>}>
        <Results resultsPromise={resultsPromise} />
      </Suspense>
    </>
  );
}
