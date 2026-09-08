// The receiver you choose decides which types satisfy an interface. The
// method set of T holds only its value-receiver methods; the method set of *T
// holds both. That rule is why a struct with one pointer-receiver method
// stops fitting an interface it looked like it fitted.

package main

import "fmt"

type Resettable interface {
	Reset()
	Total() int
}

type Tally struct{ n int }

func (t *Tally) Reset()     { t.n = 0 }
func (t Tally) Total() int  { return t.n }
func (t *Tally) Add(by int) { t.n += by }

// Compile-time assertions. The first holds; the commented line for the value
// type would not, and the lesson body shows what the compiler says about it.
var _ Resettable = (*Tally)(nil)

func main() {
	t := &Tally{}
	t.Add(3)
	t.Add(4)
	fmt.Printf("total %d\n", t.Total())

	// The pointer satisfies the interface; the value does not.
	var r Resettable = t
	r.Reset()
	fmt.Printf("after Reset via interface: %d\n", t.Total())

	// Values in a slice are addressable, so a pointer-receiver method can be
	// called on an element directly.
	tallies := []Tally{{n: 1}, {n: 2}}
	tallies[0].Add(10)
	fmt.Printf("slice element updated: %d %d\n", tallies[0].Total(), tallies[1].Total())

	// A map element is not addressable, so the same call on a map entry does
	// not compile. Read, modify, write back instead.
	byName := map[string]Tally{"a": {n: 5}}
	entry := byName["a"]
	entry.Add(1)
	byName["a"] = entry
	fmt.Printf("map entry updated: %d\n", byName["a"].Total())

	// A map of pointers avoids the round trip, at the cost of the entries no
	// longer being values.
	pointers := map[string]*Tally{"a": {n: 5}}
	pointers["a"].Add(1)
	fmt.Printf("map of pointers updated: %d\n", pointers["a"].Total())

	// Mixing receivers on one type is what produces the confusing case: Total
	// works on both, Add and Reset only on the pointer.
	value := Tally{n: 7}
	fmt.Printf("value receiver on a value: %d\n", value.Total())
	value.Add(1) // shorthand for (&value).Add(1); value is addressable
	fmt.Printf("pointer receiver on an addressable value: %d\n", value.Total())

	// The interface holds a pointer, so a later change through the pointer is
	// visible through the interface.
	t.Add(100)
	fmt.Printf("through the interface after a later Add: %d\n", r.Total())
}
