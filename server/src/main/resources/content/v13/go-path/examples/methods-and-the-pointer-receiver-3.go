// Embedding promotes the embedded type's methods to the outer type, which
// reads like inheritance and is not. There is no dynamic dispatch back into
// the outer type: a promoted method calls the methods of the type it was
// written on.

package main

import "fmt"

type Base struct{ ID int }

func (b Base) Label() string { return fmt.Sprintf("base-%d", b.ID) }

// Describe is written on Base, so the Label it calls is Base's Label, even
// when Describe is reached through a type that has its own Label.
func (b Base) Describe() string { return "describe -> " + b.Label() }

// Base is embedded: it has no field name of its own, so its field name is the
// type name and its methods are promoted to Report.
type Report struct {
	Base
	Title string
}

// Report shadows Label. Report.Describe is still Base.Describe.
func (r Report) Label() string { return fmt.Sprintf("report-%d-%s", r.ID, r.Title) }

// An interface plus a field is how the outer type actually gets to override:
// the outer type holds the behaviour it wants to vary.
type Labeller interface{ Label() string }

type Wrapper struct {
	Inner Labeller
	Extra string
}

func (w Wrapper) Describe() string { return "wrapped -> " + w.Inner.Label() + " " + w.Extra }

func main() {
	r := Report{Base: Base{ID: 7}, Title: "q3"}

	// Promotion: r.ID is r.Base.ID, and both spellings work.
	fmt.Printf("promoted field: %d %d\n", r.ID, r.Base.ID)

	// r.Label is Report's own; r.Base.Label is still reachable.
	fmt.Printf("outer Label:    %s\n", r.Label())
	fmt.Printf("inner Label:    %s\n", r.Base.Label())

	// Here is the part that surprises people: Describe was promoted from
	// Base, and it calls Base's Label, not Report's.
	fmt.Printf("promoted Describe: %s\n", r.Describe())

	// Composition with an interface field gets the behaviour people expected
	// from embedding, because the call goes through the interface.
	w := Wrapper{Inner: r, Extra: "(composed)"}
	fmt.Printf("composed Describe: %s\n", w.Describe())

	// Satisfying an interface through a promoted method: Report has Label of
	// its own, and a struct embedding Base without one would still satisfy
	// Labeller through the promotion.
	type Plain struct{ Base }
	var labellers []Labeller = []Labeller{r, Plain{Base: Base{ID: 2}}, r.Base}
	for _, l := range labellers {
		fmt.Printf("%-12T %s\n", l, l.Label())
	}

	// Embedding a pointer works too, and then the promoted methods operate on
	// whatever that pointer addresses.
	type Linked struct{ *Base }
	shared := &Base{ID: 42}
	a := Linked{Base: shared}
	b := Linked{Base: shared}
	shared.ID = 43
	fmt.Printf("shared through two embeds: %s %s\n", a.Label(), b.Label())
}
