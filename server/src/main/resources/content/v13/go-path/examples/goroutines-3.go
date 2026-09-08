// A leaked goroutine is one that is blocked on something that will never
// happen. This program observes the leak as a guarantee rather than as a
// timing accident: a send on an unbuffered channel cannot complete until a
// receive on the same channel is ready, so a producer nobody reads from is
// still stuck at its send when main looks.

package main

import (
	"fmt"
	"sync"
)

// leakyProducer sends one value and then records that it got past the send.
// If nothing receives, the recording never happens.
func leakyProducer(out chan<- int, reached chan<- string) {
	out <- 1
	reached <- "producer finished"
}

// cancellableProducer offers the same value but also watches a done channel,
// so a caller that walks away can still release it.
func cancellableProducer(out chan<- int, done <-chan struct{}, reached chan<- string) {
	select {
	case out <- 1:
		reached <- "producer sent its value"
	case <-done:
		reached <- "producer released by cancellation"
	}
}

func main() {
	// Nobody will ever receive from unread.
	unread := make(chan int)
	reached := make(chan string, 1)
	go leakyProducer(unread, reached)

	// reached is guaranteed empty: the goroutine cannot reach its second
	// statement until the first one completes, and it never will.
	select {
	case msg := <-reached:
		fmt.Println("unexpected:", msg)
	default:
		fmt.Println("leaked producer is still blocked on its send")
	}

	// The same producer, given a way out.
	done := make(chan struct{})
	released := make(chan string, 1)
	go cancellableProducer(make(chan int), done, released)
	close(done)
	fmt.Println(<-released)

	// And the same producer with a reader, which is the ordinary case. The
	// done channel here is never closed, so only the send can proceed.
	values := make(chan int)
	stillOpen := make(chan struct{})
	finished := make(chan string, 1)
	go cancellableProducer(values, stillOpen, finished)
	fmt.Println("value received:", <-values)
	fmt.Println("second producer reported:", <-finished)

	// The disciplined shape: a worker that owns its exit condition, and a
	// caller that waits for it.
	var wg sync.WaitGroup
	stop := make(chan struct{})
	results := make(chan int, 8)
	wg.Go(func() {
		for n := 1; ; n++ {
			select {
			case <-stop:
				return
			case results <- n * n:
			}
		}
	})
	sum := 0
	for range 4 {
		sum += <-results
	}
	close(stop)
	wg.Wait()
	fmt.Printf("worker produced four squares summing to %d, then exited\n", sum)
}
