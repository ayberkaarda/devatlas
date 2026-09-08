// Nothing here says "implements". A type satisfies an interface because it has
// the methods, and the check happens where the value is assigned.

package main

import (
	"fmt"
	"os"
	"strings"
)

// Notifier is declared next to the code that consumes it, not next to the
// types that satisfy it. Neither type below mentions this interface.
type Notifier interface {
	Notify(message string) string
}

type EmailChannel struct{ Address string }

func (c EmailChannel) Notify(message string) string {
	return "email to " + c.Address + ": " + message
}

type LogChannel struct{ Level string }

func (c LogChannel) Notify(message string) string {
	return "[" + c.Level + "] " + message
}

// upperWriter satisfies io.Writer, an interface declared in the standard
// library, without importing anything into its own declaration.
type upperWriter struct{ target *os.File }

func (w upperWriter) Write(p []byte) (int, error) {
	n, err := w.target.WriteString(strings.ToUpper(string(p)))
	return n, err
}

func broadcast(channels []Notifier, message string) {
	for _, c := range channels {
		fmt.Println(c.Notify(message))
	}
}

func main() {
	broadcast([]Notifier{
		EmailChannel{Address: "ops@example.com"},
		LogChannel{Level: "warn"},
	}, "disk is filling up")

	// fmt.Fprintf accepts an io.Writer. It has never heard of upperWriter and
	// does not need to.
	fmt.Fprintf(upperWriter{target: os.Stdout}, "written through a custom writer\n")

	// The empty interface -- spelled any -- is satisfied by every type, so it
	// carries a value without saying anything about it.
	var anything any = EmailChannel{Address: "ops@example.com"}
	fmt.Printf("stored in any: %v\n", anything)

	// A compile-time assertion. If EmailChannel ever loses its method this
	// line stops the build, at the declaration rather than at some caller.
	var _ Notifier = EmailChannel{}

	// An interface can embed another. Satisfying the combination means having
	// every method of every embedded interface.
	type Describer interface {
		Notifier
		fmt.Stringer
	}
	var describer Describer = Verbose{}
	fmt.Println(describer.String(), "|", describer.Notify("both methods present"))
}

type Verbose struct{}

func (Verbose) Notify(message string) string { return "verbose: " + message }

func (Verbose) String() string { return "Verbose channel" }
