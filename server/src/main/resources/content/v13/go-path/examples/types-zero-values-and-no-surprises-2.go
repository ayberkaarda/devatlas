// Conversions in Go are always written down. Nothing widens, narrows or
// changes signedness on its own, and untyped constants are the one place where
// a value has no type until it is used.

package main

import "fmt"

func main() {
	// Untyped constants have arbitrary precision until they are given a type.
	const bytesPerPage = 4096
	const third = 1.0 / 3.0

	var small int32 = 7
	var wide int64 = int64(small) // the conversion is mandatory, even widening
	fmt.Printf("int32 %d -> int64 %d\n", small, wide)

	// Float to integer truncates towards zero. It does not round.
	for _, v := range []float64{9.7, -9.7, 0.5} {
		fmt.Printf("int(%.1f) = %d\n", v, int(v))
	}

	// Unsigned arithmetic wraps, and the specification says so: the result is
	// reduced modulo 2**n for an n-bit unsigned type.
	var level uint8 = 250
	level += 10
	fmt.Printf("uint8 250+10 = %d\n", level)

	// Integer division truncates towards zero, and the remainder takes the
	// sign of the dividend.
	fmt.Printf("-7/2 = %d  -7%%2 = %d\n", -7/2, -7%2)

	// A constant expression is evaluated at compile time in full precision and
	// only then converted to the type it is used at.
	fmt.Printf("1.0/3.0 as float64 = %.10f\n", float64(third))
	fmt.Printf("1.0/3.0 as float32 = %.10f\n", float32(third))
	fmt.Printf("a page holds %d bytes\n", bytesPerPage)

	// Strings, bytes and runes are three different views of the same text.
	word := "gopher"
	fmt.Printf("bytes  %v\n", []byte(word)[:3])
	fmt.Printf("runes  %v\n", []rune(word)[:3])
	fmt.Printf("byte 0 %q rune 0 %q\n", word[0], []rune(word)[0])

	// Indexing a string yields a byte, not a character. For text outside
	// ASCII the byte count and the rune count differ.
	accented := "grün"
	fmt.Printf("len(%q) = %d bytes, %d runes\n", accented, len(accented), len([]rune(accented)))
}
