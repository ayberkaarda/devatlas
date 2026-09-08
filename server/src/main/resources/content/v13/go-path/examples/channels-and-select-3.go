// select waits on several channel operations at once. When more than one is
// ready the choice is deliberately random, so nothing here prints anything
// that depends on which case won -- only totals and counts, which do not.

package main

import (
	"fmt"
	"sort"
	"sync"
)

func worker(jobs <-chan int, results chan<- int, wg *sync.WaitGroup) {
	defer wg.Done()
	for n := range jobs {
		results <- n * n
	}
}

func main() {
	// A fan-out over three workers and a fan-in on one results channel. Which
	// worker handles which job is not specified; the set of results is.
	jobs := make(chan int, 8)
	results := make(chan int, 8)
	var wg sync.WaitGroup
	for range 3 {
		wg.Add(1)
		go worker(jobs, results, &wg)
	}
	for n := 1; n <= 8; n++ {
		jobs <- n
	}
	close(jobs)
	wg.Wait()
	close(results)

	var collected []int
	for v := range results {
		collected = append(collected, v)
	}
	sort.Ints(collected)
	fmt.Printf("all %d squares arrived: %v\n", len(collected), collected)

	// Two producers merged with select. The loop keeps going until both
	// inputs are closed, and setting a channel variable to nil removes its
	// case from the select -- a receive on a nil channel blocks forever, so
	// the case can never be chosen again.
	evens := make(chan int)
	odds := make(chan int)
	go func() {
		for n := 2; n <= 10; n += 2 {
			evens <- n
		}
		close(evens)
	}()
	go func() {
		for n := 1; n <= 9; n += 2 {
			odds <- n
		}
		close(odds)
	}()

	sum, count := 0, 0
	for evens != nil || odds != nil {
		select {
		case v, ok := <-evens:
			if !ok {
				evens = nil
				continue
			}
			sum += v
			count++
		case v, ok := <-odds:
			if !ok {
				odds = nil
				continue
			}
			sum += v
			count++
		}
	}
	fmt.Printf("merged %d values summing to %d\n", count, sum)

	// A select with only a default never blocks, which is how a non-blocking
	// poll is written.
	idle := make(chan int)
	select {
	case v := <-idle:
		fmt.Println("unexpected", v)
	default:
		fmt.Println("nothing ready, moving on")
	}

	// Cancellation: a closed channel is always ready, so every select
	// watching it becomes ready at once.
	quit := make(chan struct{})
	stopped := make(chan int, 4)
	var wg2 sync.WaitGroup
	for id := range 4 {
		wg2.Go(func() {
			for {
				select {
				case <-quit:
					stopped <- id
					return
				default:
				}
			}
		})
	}
	close(quit)
	wg2.Wait()
	close(stopped)
	var ids []int
	for id := range stopped {
		ids = append(ids, id)
	}
	sort.Ints(ids)
	fmt.Printf("every watcher stopped: %v\n", ids)
}
