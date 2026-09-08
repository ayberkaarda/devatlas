// A named type is a distinct type, not an alias, and a struct value is a
// value: it is copied on assignment and compared field by field.

package main

import "fmt"

// Celsius and Fahrenheit share float64 as their underlying type and are still
// two different types. Mixing them without a conversion does not compile.
type Celsius float64

type Fahrenheit float64

func (c Celsius) toFahrenheit() Fahrenheit { return Fahrenheit(c*9/5) + 32 }

// Config is comparable: every field is comparable, so == is defined for it.
type Config struct {
	Host    string
	Port    int
	Verbose bool
}

func main() {
	boiling := Celsius(100)
	fmt.Printf("%.1fC = %.1fF\n", float64(boiling), float64(boiling.toFahrenheit()))

	// The conversion has to be written even though the underlying type is the
	// same, which is the point of declaring the two types separately.
	var asF Fahrenheit = Fahrenheit(boiling)
	fmt.Printf("reinterpreted, not converted: %.1f\n", float64(asF))

	// The zero Config is a complete, usable value and equals a composite
	// literal with no fields set.
	var zero Config
	fmt.Printf("zero value      %+v\n", zero)
	fmt.Printf("zero == Config{} %v\n", zero == Config{})

	// Assignment copies the whole struct. The two values are independent.
	original := Config{Host: "localhost", Port: 8080}
	copied := original
	copied.Port = 9090
	fmt.Printf("original %+v\n", original)
	fmt.Printf("copy     %+v\n", copied)
	fmt.Printf("equal    %v\n", original == copied)

	// Passing a struct to a function copies it too, so the callee cannot
	// change what the caller holds.
	silence(original)
	fmt.Printf("after call %+v\n", original)

	// A struct used as a map key relies on that same comparability.
	seen := map[Config]string{
		{Host: "localhost", Port: 8080}: "primary",
		{Host: "localhost", Port: 9090}: "standby",
	}
	fmt.Printf("lookup   %s\n", seen[original])
	fmt.Printf("lookup   %s\n", seen[copied])
}

func silence(c Config) {
	c.Verbose = false
	c.Host = "discarded"
}
