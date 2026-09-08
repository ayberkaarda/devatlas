// Two slices cut from the same array share storage, and append writes into
// that storage whenever there is room. The exact capacity append chooses when
// it has to grow is not specified, so this program reports whether sharing
// held rather than printing a capacity that a future release could change.

package main

import "fmt"

// shares reports whether writing through b is visible through a, which is the
// property that matters and the one that stays true on every machine.
func shares(a, b []int) bool {
	if len(a) == 0 || len(b) == 0 {
		return false
	}
	saved := b[0]
	b[0] = saved ^ 0x5f5f
	changed := false
	for _, v := range a {
		if v == b[0] {
			changed = true
			break
		}
	}
	b[0] = saved
	return changed
}

func main() {
	base := []int{0, 1, 2, 3, 4, 5, 6, 7}
	fmt.Printf("base   %v len=%d cap=%d\n", base, len(base), cap(base))

	// A slice expression keeps the rest of the backing array in its capacity:
	// the specification defines cap(a[low:high]) as cap(a) - low.
	head := base[0:3]
	fmt.Printf("head   %v len=%d cap=%d\n", head, len(head), cap(head))
	fmt.Printf("head shares storage with base: %v\n", shares(base, head))

	// So appending to head has room, and it overwrites base[3].
	head = append(head, 100)
	fmt.Printf("after append to head, base is %v\n", base)

	// The three-index form caps the slice at its own length, so the next
	// append has nowhere to go and must allocate.
	base = []int{0, 1, 2, 3, 4, 5, 6, 7}
	guarded := base[0:3:3]
	fmt.Printf("guarded %v len=%d cap=%d\n", guarded, len(guarded), cap(guarded))
	before := cap(guarded)
	guarded = append(guarded, 100)
	fmt.Printf("append had to grow: %v\n", cap(guarded) > before)
	fmt.Printf("base untouched: %v\n", base)
	fmt.Printf("guarded still shares with base: %v\n", shares(base, guarded))

	// copy is the explicit way to get storage of your own. It copies the
	// smaller of the two lengths and returns that count.
	independent := make([]int, len(base))
	n := copy(independent, base)
	independent[0] = 42
	fmt.Printf("copied %d elements; base %v independent %v\n", n, base, independent)

	// A short destination is not an error; copy simply stops.
	short := make([]int, 3)
	fmt.Printf("copy into len 3 moved %d elements: %v\n", copy(short, base), short)
}
