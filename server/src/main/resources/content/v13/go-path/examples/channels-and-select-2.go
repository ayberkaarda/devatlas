// A buffered channel decouples the sender from the receiver up to its
// capacity, and a directional channel type lets a signature say which end a
// function is allowed to hold.

package main

import "fmt"

// produce may only send. Trying to receive from out does not compile.
func produce(out chan<- int, count int) {
	for n := range count {
		out <- n
	}
	close(out)
}

// consume may only receive, and it cannot close the channel either.
func consume(in <-chan int) int {
	total := 0
	for v := range in {
		total += v
	}
	return total
}

func main() {
	// Capacity three: three sends complete without any receiver.
	buffered := make(chan string, 3)
	buffered <- "a"
	buffered <- "b"
	buffered <- "c"
	fmt.Printf("len=%d cap=%d\n", len(buffered), cap(buffered))

	// A fourth send would block, so this asks first.
	select {
	case buffered <- "d":
		fmt.Println("room for a fourth")
	default:
		fmt.Println("buffer full, send skipped")
	}

	fmt.Printf("drained: %s %s %s\n", <-buffered, <-buffered, <-buffered)
	fmt.Printf("len=%d cap=%d\n", len(buffered), cap(buffered))

	// A non-blocking receive from an empty channel.
	select {
	case v := <-buffered:
		fmt.Println("got", v)
	default:
		fmt.Println("nothing buffered")
	}

	// Directional types at the call boundary. The same bidirectional channel
	// is passed to both, and each side gets only the half it needs.
	numbers := make(chan int, 4)
	go produce(numbers, 6)
	fmt.Printf("sum of 0..5 = %d\n", consume(numbers))

	// A buffered channel of empty structs is the usual counting semaphore:
	// capacity is the number of holders allowed at once.
	slots := make(chan struct{}, 2)
	acquired := 0
	for range 4 {
		select {
		case slots <- struct{}{}:
			acquired++
		default:
		}
	}
	fmt.Printf("semaphore of capacity %d admitted %d of 4\n", cap(slots), acquired)

	// An unbuffered channel has capacity zero, which is what makes it a
	// handshake rather than a queue.
	fmt.Printf("unbuffered cap = %d\n", cap(make(chan int)))
}
