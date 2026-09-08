// An unbuffered channel is a handshake: the specification says communication
// succeeds only when both a sender and a receiver are ready. The memory model
// then says a send on a channel is synchronized before the completion of the
// corresponding receive, which is why the values below arrive in the order
// they were sent.

package main

import "fmt"

func main() {
	squares := make(chan int)

	// One producer, one channel: the receives observe the sends in order.
	// That ordering is a rule, not an accident of scheduling.
	go func() {
		for n := 1; n <= 5; n++ {
			squares <- n * n
		}
		close(squares)
	}()

	// Ranging over a channel receives until it is closed and drained.
	for v := range squares {
		fmt.Printf("received %d\n", v)
	}
	fmt.Println("channel closed, range ended")

	// A receive from a closed channel returns immediately with the element
	// type's zero value. The two-result form is how you tell that apart from
	// a real zero.
	v, ok := <-squares
	fmt.Printf("after close: value=%d ok=%v\n", v, ok)

	// Closing is a producer's job and it is a broadcast: every receiver sees
	// it. Here two receivers wait on the same signal.
	start := make(chan struct{})
	ready := make(chan string, 2)
	go func() { <-start; ready <- "runner A released" }()
	go func() { <-start; ready <- "runner B released" }()
	close(start)
	first, second := <-ready, <-ready
	fmt.Printf("both runners released: %v\n", (first != second) && first != "" && second != "")

	// A handshake in both directions: each send waits for the matching
	// receive, so the alternation is guaranteed rather than likely.
	ping := make(chan int)
	pong := make(chan int)
	go func() {
		for n := range ping {
			pong <- n * 10
		}
		close(pong)
	}()
	for n := 1; n <= 3; n++ {
		ping <- n
		fmt.Printf("sent %d, got back %d\n", n, <-pong)
	}
	close(ping)
	fmt.Printf("pong closed too: %v\n", func() bool { _, open := <-pong; return !open }())
}
