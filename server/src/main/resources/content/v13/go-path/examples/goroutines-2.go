// What a goroutine closes over, and what happens when two of them close over
// the same thing. The loop variable question changed in Go 1.22: the variables
// declared by a range clause are now new on every iteration, so a goroutine
// started in the loop captures that iteration's variable.

package main

import (
	"fmt"
	"sort"
	"sync"
)

func main() {
	// Each goroutine captures its own i, so the five values collected are
	// 0..4 exactly once each. They arrive in no particular order, which is why
	// this sorts before printing.
	var mu sync.Mutex
	var captured []int

	var wg sync.WaitGroup
	for i := range 5 {
		wg.Go(func() {
			mu.Lock()
			captured = append(captured, i)
			mu.Unlock()
		})
	}
	wg.Wait()
	sort.Ints(captured)
	fmt.Printf("captured %v\n", captured)

	// A variable declared outside the loop is shared by every goroutine, which
	// is the same hazard the range clause used to have. Incrementing it without
	// a lock is a data race; with one, the total is exact.
	shared := 0
	var wg2 sync.WaitGroup
	for range 1000 {
		wg2.Go(func() {
			mu.Lock()
			shared++
			mu.Unlock()
		})
	}
	wg2.Wait()
	fmt.Printf("guarded increments: %d\n", shared)

	// sync.Once runs its function exactly once no matter how many goroutines
	// reach it, and every caller is blocked until that run has finished.
	var once sync.Once
	initialised := 0
	var wg3 sync.WaitGroup
	for range 50 {
		wg3.Go(func() {
			once.Do(func() {
				mu.Lock()
				initialised++
				mu.Unlock()
			})
		})
	}
	wg3.Wait()
	fmt.Printf("sync.Once ran the initialiser %d time(s)\n", initialised)

	// Arguments are evaluated when the go statement runs, not when the
	// goroutine is scheduled, so this passes the value that was current then.
	done := make(chan string, 3)
	for _, name := range []string{"first", "second", "third"} {
		go func(n string) { done <- n }(name)
	}
	received := []string{<-done, <-done, <-done}
	sort.Strings(received)
	fmt.Printf("received %v\n", received)
}
