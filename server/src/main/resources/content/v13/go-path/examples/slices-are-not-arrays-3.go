// The range clause hands you a copy of each element. For a slice of structs
// that copy is where an edit quietly goes missing, and the repair is to reach
// the element through its index or to hold pointers on purpose.

package main

import "fmt"

type Task struct {
	Name string
	Done bool
}

func main() {
	tasks := []Task{{Name: "write"}, {Name: "review"}, {Name: "ship"}}

	// v is a fresh Task on every iteration. Writing to it changes nothing.
	for _, v := range tasks {
		v.Done = true
	}
	fmt.Printf("after ranging by value  %v\n", tasks)

	// The index names the element itself.
	for i := range tasks {
		tasks[i].Done = true
	}
	fmt.Printf("after ranging by index  %v\n", tasks)

	// A slice of pointers behaves differently, because the copy is of the
	// pointer and both copies address the same struct.
	pointers := []*Task{{Name: "write"}, {Name: "review"}}
	for _, p := range pointers {
		p.Done = true
	}
	for _, p := range pointers {
		fmt.Printf("through pointer %+v\n", *p)
	}

	// The range expression is evaluated once, so appending inside the loop
	// does not extend the loop. This terminates.
	source := []int{1, 2, 3}
	for _, v := range source {
		source = append(source, v*10)
	}
	fmt.Printf("appended while ranging  %v\n", source)

	// Truncating to zero length keeps the capacity, which is the idiomatic
	// way to reuse a buffer without allocating again.
	buffer := make([]int, 0, 8)
	buffer = append(buffer, 1, 2, 3)
	capacityBefore := cap(buffer)
	buffer = buffer[:0]
	fmt.Printf("truncated len=%d capacity kept=%v\n", len(buffer), cap(buffer) == capacityBefore)

	// Deleting an element by shifting the tail down: the last slot still
	// holds the old value until the slice is reslice-shortened past it.
	values := []string{"a", "b", "c", "d"}
	values = append(values[:1], values[2:]...)
	fmt.Printf("after deleting index 1  %v len=%d\n", values, len(values))
}
