// What the race detector is looking for, and the three ways to not give it
// anything to find: give each goroutine storage of its own, guard shared
// storage with a mutex, or use sync/atomic. All three totals below are exact,
// which is the point -- a synchronised program has one answer, not a likely
// one.

package main

import (
	"fmt"
	"sort"
	"sync"
	"sync/atomic"
)

const workers = 500

func main() {
	// 1. No sharing at all. Each goroutine writes to its own slot, and the
	// slice header is read-only for the duration.
	perWorker := make([]int, workers)
	var wg sync.WaitGroup
	for i := range workers {
		wg.Go(func() { perWorker[i] = i })
	}
	wg.Wait()
	sum := 0
	for _, v := range perWorker {
		sum += v
	}
	fmt.Printf("private slots:     sum=%d\n", sum)

	// 2. A mutex around the shared variable. Lock and Unlock establish the
	// ordering the memory model needs, so every increment is observed.
	var mu sync.Mutex
	guarded := 0
	var wg2 sync.WaitGroup
	for range workers {
		wg2.Go(func() {
			mu.Lock()
			defer mu.Unlock()
			guarded++
		})
	}
	wg2.Wait()
	fmt.Printf("mutex:             guarded=%d\n", guarded)

	// 3. sync/atomic, for a single value that only needs to be incremented.
	var counter atomic.Int64
	var wg3 sync.WaitGroup
	for range workers {
		wg3.Go(func() { counter.Add(1) })
	}
	wg3.Wait()
	fmt.Printf("atomic:            counter=%d\n", counter.Load())

	// A guarded map. Maps are not safe for concurrent use even when the keys
	// are distinct, so the lock covers the write, not just the value.
	tally := map[string]int{}
	var wg4 sync.WaitGroup
	for i := range workers {
		wg4.Go(func() {
			key := fmt.Sprintf("bucket-%d", i%4)
			mu.Lock()
			tally[key]++
			mu.Unlock()
		})
	}
	wg4.Wait()
	keys := make([]string, 0, len(tally))
	for k := range tally {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	for _, k := range keys {
		fmt.Printf("guarded map:       %s=%d\n", k, tally[k])
	}

	// Errors from concurrent work, collected without a race. Each worker
	// writes its own slot; the caller reduces afterwards.
	results := make([]error, workers)
	var wg5 sync.WaitGroup
	for i := range workers {
		wg5.Go(func() {
			if i%100 == 0 {
				results[i] = fmt.Errorf("worker %d refused", i)
			}
		})
	}
	wg5.Wait()
	failed := 0
	for _, err := range results {
		if err != nil {
			failed++
		}
	}
	fmt.Printf("collected errors:  %d of %d workers failed\n", failed, workers)
}
