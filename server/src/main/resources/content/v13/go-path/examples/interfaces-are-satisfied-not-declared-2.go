// An interface value is a pair: the dynamic type of what it holds, and the
// value itself. A type assertion asks about the first half, and the two-result
// form asks without risking a panic.

package main

import "fmt"

type Shape interface{ Area() float64 }

type Rect struct{ W, H float64 }

func (r Rect) Area() float64 { return r.W * r.H }

type Square struct{ Side float64 }

func (s Square) Area() float64 { return s.Side * s.Side }

func (s Square) Diagonal() float64 { return s.Side * 1.4142135623730951 }

// describe uses a type switch, which is the readable form when there are
// several cases. The default branch is what makes it total.
func describe(s Shape) string {
	switch v := s.(type) {
	case Square:
		return fmt.Sprintf("square of side %.1f, diagonal %.3f", v.Side, v.Diagonal())
	case Rect:
		return fmt.Sprintf("rectangle %.1f by %.1f", v.W, v.H)
	default:
		return fmt.Sprintf("some shape of area %.1f", v.Area())
	}
}

type Circle struct{ R float64 }

func (c Circle) Area() float64 { return 3.141592653589793 * c.R * c.R }

func main() {
	shapes := []Shape{Rect{W: 3, H: 4}, Square{Side: 5}, Circle{R: 1}}
	for _, s := range shapes {
		fmt.Printf("%-9T area %6.3f  %s\n", s, s.Area(), describe(s))
	}

	// The two-result assertion never panics; it reports whether the dynamic
	// type matched.
	for _, s := range shapes {
		if sq, ok := s.(Square); ok {
			fmt.Printf("found a square, diagonal %.3f\n", sq.Diagonal())
		} else {
			fmt.Printf("%T is not a Square\n", s)
		}
	}

	// A nil interface holds no type at all. That is different from an
	// interface holding a typed nil pointer.
	var empty Shape
	fmt.Printf("nil interface: value=%v type=%T isNil=%v\n", empty, empty, empty == nil)

	var missing *Rect
	var holding Shape = missing
	fmt.Printf("typed nil:     type=%T isNil=%v\n", holding, holding == nil)

	// Asserting to another interface asks whether the dynamic type has that
	// interface's methods.
	type Diagonaler interface{ Diagonal() float64 }
	for _, s := range shapes {
		d, ok := s.(Diagonaler)
		fmt.Printf("%-9T satisfies Diagonaler: %v", s, ok)
		if ok {
			fmt.Printf(" (%.3f)", d.Diagonal())
		}
		fmt.Println()
	}
}
