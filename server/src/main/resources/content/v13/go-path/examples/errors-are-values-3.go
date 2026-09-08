// Two things a caller has to know about error values: several errors can
// travel together, and an interface holding a nil pointer is not a nil
// interface.

package main

import (
	"errors"
	"fmt"
	"strings"
)

var (
	ErrTooShort = errors.New("too short")
	ErrNoDigit  = errors.New("no digit")
	ErrNoUpper  = errors.New("no upper case letter")
)

// validate collects every failure instead of stopping at the first, which is
// what a form wants. errors.Join returns nil when every argument is nil.
func validate(password string) error {
	var problems []error
	if len(password) < 8 {
		problems = append(problems, ErrTooShort)
	}
	if !strings.ContainsAny(password, "0123456789") {
		problems = append(problems, ErrNoDigit)
	}
	if strings.ToLower(password) == password {
		problems = append(problems, ErrNoUpper)
	}
	return errors.Join(problems...)
}

// ConfigError is deliberately declared with a pointer receiver, which is the
// setup for the trap below.
type ConfigError struct{ Key string }

func (e *ConfigError) Error() string { return "bad config key " + e.Key }

// checkTrap returns the concrete pointer type. When there is nothing wrong the
// pointer is nil, but the interface value it is assigned to is not: it carries
// the type *ConfigError alongside the nil pointer.
func checkTrap(key string) error {
	var problem *ConfigError
	if key == "" {
		problem = &ConfigError{Key: "(empty)"}
	}
	return problem
}

// checkFixed returns the interface's own nil in the success case, which is the
// only way to produce a nil error.
func checkFixed(key string) error {
	if key == "" {
		return &ConfigError{Key: "(empty)"}
	}
	return nil
}

func main() {
	for _, pw := range []string{"Passw0rdLong", "short", "alllowercase"} {
		err := validate(pw)
		if err == nil {
			fmt.Printf("%-13s accepted\n", pw)
			continue
		}
		fmt.Printf("%-13s rejected: short=%v digit=%v upper=%v\n",
			pw,
			errors.Is(err, ErrTooShort),
			errors.Is(err, ErrNoDigit),
			errors.Is(err, ErrNoUpper))
	}

	fmt.Printf("join of no errors is nil: %v\n", errors.Join() == nil)

	// The trap, reported as a property rather than printed: calling Error on
	// the nil pointer inside would panic.
	trapped := checkTrap("timeout")
	fmt.Printf("checkTrap on a good key returned err != nil: %v\n", trapped != nil)

	fixed := checkFixed("timeout")
	fmt.Printf("checkFixed on a good key returned err != nil: %v\n", fixed != nil)
	fmt.Printf("checkFixed on a bad key: %v\n", checkFixed(""))
}
