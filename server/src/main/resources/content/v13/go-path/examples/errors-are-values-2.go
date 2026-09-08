// Wrapping adds context without hiding what happened. fmt.Errorf with %w
// keeps the original error reachable; errors.Is walks the chain looking for a
// particular value and errors.As looks for a particular type.

package main

import (
	"errors"
	"fmt"
)

var ErrMissingField = errors.New("missing field")

// FieldError carries structured detail a caller can act on, which a message
// string cannot.
type FieldError struct {
	Field string
	Line  int
}

func (e *FieldError) Error() string {
	return fmt.Sprintf("field %q at line %d", e.Field, e.Line)
}

// Unwrap makes FieldError part of a chain, so errors.Is finds the sentinel
// underneath it.
func (e *FieldError) Unwrap() error { return ErrMissingField }

func parseRecord(line int, raw string) error {
	if raw == "" {
		return &FieldError{Field: "name", Line: line}
	}
	return nil
}

func loadFile(name string) error {
	if err := parseRecord(4, ""); err != nil {
		return fmt.Errorf("load %s: %w", name, err)
	}
	return nil
}

func main() {
	err := loadFile("accounts.csv")
	fmt.Printf("message: %v\n", err)

	// Is walks the chain: the wrapper, then the FieldError, then the sentinel
	// its Unwrap returns.
	fmt.Printf("errors.Is ErrMissingField: %v\n", errors.Is(err, ErrMissingField))

	// As finds the first error in the chain assignable to the target and
	// gives the caller the structured detail.
	var fieldErr *FieldError
	if errors.As(err, &fieldErr) {
		fmt.Printf("errors.As found field=%s line=%d\n", fieldErr.Field, fieldErr.Line)
	}

	// Unwrap peels exactly one layer.
	inner := errors.Unwrap(err)
	fmt.Printf("one layer down: %v\n", inner)
	fmt.Printf("two layers down: %v\n", errors.Unwrap(inner))
	fmt.Printf("three layers down: %v\n", errors.Unwrap(errors.Unwrap(inner)))

	// %v formats the same text but wraps nothing, so the chain stops there.
	opaque := fmt.Errorf("load %s: %v", "accounts.csv", &FieldError{Field: "name", Line: 4})
	fmt.Printf("formatted with %%v: %v\n", opaque)
	fmt.Printf("errors.Is through %%v: %v\n", errors.Is(opaque, ErrMissingField))
	fmt.Printf("errors.As through %%v: %v\n", errors.As(opaque, &fieldErr))
}
