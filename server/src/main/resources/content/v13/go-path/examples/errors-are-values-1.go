// An error is an ordinary value of an ordinary interface type. It is returned,
// inspected and compared like anything else, and the only special support the
// language gives it is that the interface is predeclared.

package main

import (
	"errors"
	"fmt"
)

// Sentinel errors are package-level values, so a caller can name the exact
// condition instead of matching on a message.
var (
	ErrNotFound = errors.New("account not found")
	ErrFrozen   = errors.New("account is frozen")
)

type account struct {
	id      string
	balance int
	frozen  bool
}

var ledger = map[string]account{
	"a-1": {id: "a-1", balance: 120},
	"a-2": {id: "a-2", balance: 40, frozen: true},
}

func withdraw(id string, amount int) (int, error) {
	acct, ok := ledger[id]
	if !ok {
		return 0, ErrNotFound
	}
	if acct.frozen {
		return 0, ErrFrozen
	}
	if amount > acct.balance {
		return 0, fmt.Errorf("balance %d is short of %d", acct.balance, amount)
	}
	return acct.balance - amount, nil
}

func main() {
	// The result and the error are two return values. Checking the error
	// first is what makes the result meaningful.
	cases := []struct {
		id     string
		amount int
	}{
		{"a-1", 20},
		{"a-1", 500},
		{"a-2", 10},
		{"a-9", 10},
	}

	for _, c := range cases {
		remaining, err := withdraw(c.id, c.amount)
		switch {
		case errors.Is(err, ErrNotFound):
			fmt.Printf("%s: no such account\n", c.id)
		case errors.Is(err, ErrFrozen):
			fmt.Printf("%s: frozen, refusing\n", c.id)
		case err != nil:
			fmt.Printf("%s: refused: %v\n", c.id, err)
		default:
			fmt.Printf("%s: ok, %d left\n", c.id, remaining)
		}
	}

	// Two errors made by errors.New are never equal, even with the same text.
	// That is why a sentinel is a variable and not a literal at the call site.
	first := errors.New("same words")
	second := errors.New("same words")
	fmt.Printf("distinct values with equal text: %v\n", first == second)
	fmt.Printf("a sentinel compares to itself: %v\n", ErrNotFound == ErrNotFound)
}
