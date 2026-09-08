// An array is a value with its length in its type. A slice is a small header
// -- pointer, length, capacity -- that describes a window onto an array it
// does not own.

package main

import "fmt"

func mutateArray(a [4]int) { a[0] = 999 }

func mutateSlice(s []int) { s[0] = 999 }

func appendToSlice(s []int) { s = append(s, 999) }

func main() {
	// [4]int and [5]int are different types. The length is part of the type.
	array := [4]int{10, 20, 30, 40}
	slice := []int{10, 20, 30, 40}

	// Assigning an array copies all four elements.
	arrayCopy := array
	arrayCopy[0] = 1
	fmt.Printf("array      %v  after copy was written %v\n", array, arrayCopy)

	// Assigning a slice copies only the header. Both headers point at the
	// same backing array.
	sliceCopy := slice
	sliceCopy[0] = 1
	fmt.Printf("slice      %v  after copy was written %v\n", slice, sliceCopy)

	// The same difference shows up at a call boundary.
	mutateArray(array)
	fmt.Printf("array after mutateArray  %v\n", array)
	mutateSlice(slice)
	fmt.Printf("slice after mutateSlice  %v\n", slice)

	// A slice header is passed by value, so a callee that appends changes its
	// own header and the caller never sees the new element.
	appendToSlice(slice)
	fmt.Printf("slice after appendToSlice %v len=%d\n", slice, len(slice))

	// Arrays are comparable when their element type is; slices are not
	// comparable at all, only to nil.
	fmt.Printf("[4]int equality  %v\n", array == [4]int{10, 20, 30, 40})

	// Slicing an array produces a slice that aliases it.
	window := array[1:3]
	window[0] = 77
	fmt.Printf("array %v  window %v len=%d cap=%d\n", array, window, len(window), cap(window))
}
