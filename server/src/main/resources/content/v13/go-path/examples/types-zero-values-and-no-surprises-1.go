// Every declared variable starts at the zero value for its type. There is no
// uninitialised state in Go, so a declaration is already a complete value.

package main

import "fmt"

type Point struct{ X, Y int }

// Reader is declared only so that an interface variable can be zero here.
type Reader interface{ Read() string }

func main() {
	var (
		count   int
		ratio   float64
		enabled bool
		name    string
		letter  rune
		origin  Point
		next    *Point
		convert func(int) int
		source  Reader
		labels  []string
		lookup  map[string]int
	)

	// Value kinds: the zero value is a usable value of that type.
	fmt.Printf("int      %v\n", count)
	fmt.Printf("float64  %v\n", ratio)
	fmt.Printf("bool     %v\n", enabled)
	fmt.Printf("string   %q\n", name)
	fmt.Printf("rune     %v\n", letter)
	fmt.Printf("struct   %v\n", origin)

	// Reference kinds: the zero value is nil. Printing the identity of a live
	// pointer or func would print an address, which differs on every run, so
	// these report whether they are nil instead.
	fmt.Printf("pointer  %v\n", next)
	fmt.Printf("func     nil=%v\n", convert == nil)
	fmt.Printf("iface    %v\n", source)

	// A nil slice and a nil map are not broken values. Length, capacity and
	// reads all work; only writing to a nil map does not.
	fmt.Printf("slice    %v len=%d cap=%d nil=%v\n", labels, len(labels), cap(labels), labels == nil)
	fmt.Printf("map      %v len=%d nil=%v\n", lookup, len(lookup), lookup == nil)
	fmt.Printf("missing key reads as %v\n", lookup["absent"])

	labels = append(labels, "append allocates for a nil slice")
	fmt.Printf("appended %v\n", labels)

	// A struct's zero value is the zero value of every field, recursively.
	origin.X = 3
	fmt.Printf("reset    %v\n", Point{})
	fmt.Printf("mutated  %v\n", origin)
}
