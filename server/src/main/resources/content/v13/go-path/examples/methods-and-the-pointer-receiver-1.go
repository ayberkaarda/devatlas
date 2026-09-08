// A value receiver gets a copy of the struct; a pointer receiver gets the
// address of the one the caller holds. Everything else about the two forms is
// the same, and the call site looks identical because the compiler takes the
// address for you when it can.

package main

import "fmt"

type Counter struct {
	Name string
	N    int
}

// Value receiver: this increments a copy that is discarded when it returns.
func (c Counter) AddByValue() { c.N++ }

// Pointer receiver: this increments the caller's struct.
func (c *Counter) AddByPointer() { c.N++ }

// A value receiver is the right choice for a method that only reads.
func (c Counter) String() string { return fmt.Sprintf("%s=%d", c.Name, c.N) }

// A pointer receiver may be called on a nil pointer as long as it does not
// dereference it. That is a real technique, not a curiosity: it lets the zero
// value of a pointer type answer questions.
type node struct {
	Value int
	Next  *node
}

func (n *node) Len() int {
	if n == nil {
		return 0
	}
	return 1 + n.Next.Len()
}

func main() {
	c := Counter{Name: "hits"}

	c.AddByValue()
	c.AddByValue()
	fmt.Printf("after two AddByValue:   %v\n", c)

	// c is addressable, so this is shorthand for (&c).AddByPointer().
	c.AddByPointer()
	c.AddByPointer()
	fmt.Printf("after two AddByPointer: %v\n", c)

	// Through an explicit pointer, both forms still compile: the compiler
	// dereferences for the value receiver.
	p := &c
	p.AddByValue()
	p.AddByPointer()
	fmt.Printf("through a pointer:      %v\n", c)

	// A method value binds the receiver at the moment it is taken. Taking it
	// from a value copies; taking it from a pointer does not.
	bound := c.AddByValue
	boundPtr := p.AddByPointer
	bound()
	boundPtr()
	fmt.Printf("after bound calls:      %v\n", c)

	// A method expression makes the receiver an ordinary first argument.
	add := (*Counter).AddByPointer
	add(&c)
	fmt.Printf("via method expression:  %v\n", c)

	// Nil receiver, no dereference, no panic.
	var empty *node
	fmt.Printf("length of a nil list:   %d\n", empty.Len())
	chain := &node{Value: 1, Next: &node{Value: 2, Next: &node{Value: 3}}}
	fmt.Printf("length of a real list:  %d\n", chain.Len())
}
