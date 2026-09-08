// Five goroutines run concurrently and the program still prints the same
// thing every time, because nothing here depends on the order they finish in.
// Each writes to a slot of its own, and Wait is the point where every write is
// guaranteed to be visible.

package main

import (
	"fmt"
	"sync"
)

func checksum(word string) int {
	total := 0
	for _, r := range word {
		total += int(r)
	}
	return total
}

func main() {
	words := []string{"alpha", "bravo", "charlie", "delta", "echo"}

	// One slot per worker. No two goroutines touch the same element, so no
	// lock is needed for the writes themselves.
	sums := make([]int, len(words))

	var wg sync.WaitGroup
	for i, word := range words {
		wg.Add(1)
		go func() {
			defer wg.Done()
			sums[i] = checksum(word)
		}()
	}
	wg.Wait()

	// In the terminology of the Go memory model, a call to Done synchronizes
	// before the return of the Wait it unblocks, so every write to sums is
	// visible here.
	total := 0
	for i, word := range words {
		fmt.Printf("%-8s %d\n", word, sums[i])
		total += sums[i]
	}
	fmt.Printf("all %d results present, total %d\n", len(words), total)

	// The same fan-out written with WaitGroup.Go, which pairs the Add and the
	// Done so neither can be forgotten.
	var wg2 sync.WaitGroup
	lengths := make([]int, len(words))
	for i, word := range words {
		wg2.Go(func() { lengths[i] = len(word) })
	}
	wg2.Wait()
	fmt.Printf("lengths %v\n", lengths)

	// A goroutine is not a thread. Ten thousand of them is unremarkable, and
	// the count that comes back is exact because Wait waited for all of them.
	var wg3 sync.WaitGroup
	var mu sync.Mutex
	counted := 0
	for range 10000 {
		wg3.Go(func() {
			mu.Lock()
			counted++
			mu.Unlock()
		})
	}
	wg3.Wait()
	fmt.Printf("ten thousand goroutines finished: counted=%d\n", counted)
}
