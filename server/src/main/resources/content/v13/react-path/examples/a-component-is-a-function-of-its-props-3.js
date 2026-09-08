// The same component written for React 19. It is not executed anywhere in this
// corpus: JSX is not valid input to a plain Node run, and this repository does
// not install React. Read it for shape, not for a recorded result.
//
// Two React 19 details appear here. `ref` arrives as an ordinary prop, so no
// forwardRef wrapper is needed, and the ref callback returns a cleanup function
// that React calls when the element leaves the DOM.

import { useId, useRef } from "react";

function Field({ label, value, onChange, ref, kind = "text" }) {
  const id = useId();
  return (
    <p>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        ref={ref}
        type={kind}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </p>
  );
}

export default function SignInForm({ email, password, onEmail, onPassword }) {
  const measured = useRef([]);

  return (
    <form>
      <Field
        label="Email"
        value={email}
        onChange={onEmail}
        ref={(node) => {
          measured.current.push(node);
          return () => {
            measured.current = measured.current.filter((n) => n !== node);
          };
        }}
      />
      <Field label="Password" kind="password" value={password} onChange={onPassword} />
    </form>
  );
}
