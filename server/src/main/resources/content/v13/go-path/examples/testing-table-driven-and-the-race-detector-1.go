// The table-driven shape, run as a program. In a package this table would sit
// inside a function named TestSplitHostPort in a file named parse_test.go and
// be run by go test; the lesson body shows that version and what go test
// printed. Everything else -- the anonymous struct, the named cases, the
// message that reports input, got and want -- is identical.

package main

import (
	"errors"
	"fmt"
	"strconv"
	"strings"
)

var ErrNoPort = errors.New("no port")

func SplitHostPort(address string) (string, int, error) {
	host, portText, found := strings.Cut(address, ":")
	if !found {
		return "", 0, ErrNoPort
	}
	port, err := strconv.Atoi(portText)
	if err != nil {
		return "", 0, fmt.Errorf("port %q: %w", portText, strconv.ErrSyntax)
	}
	return host, port, nil
}

func main() {
	cases := []struct {
		name     string
		in       string
		wantHost string
		wantPort int
		wantErr  error
	}{
		{name: "host and port", in: "example.com:8080", wantHost: "example.com", wantPort: 8080},
		{name: "empty host", in: ":443", wantHost: "", wantPort: 443},
		{name: "no colon", in: "example.com", wantErr: ErrNoPort},
		{name: "port not a number", in: "example.com:http", wantErr: strconv.ErrSyntax},
	}

	failures := 0
	for _, c := range cases {
		host, port, err := SplitHostPort(c.in)

		// errors.Is compares against nil correctly too, so one check covers
		// both the success and the failure rows of the table.
		if !errors.Is(err, c.wantErr) {
			fmt.Printf("FAIL %-18s SplitHostPort(%q) error = %v, want %v\n", c.name, c.in, err, c.wantErr)
			failures++
			continue
		}
		if c.wantErr != nil {
			fmt.Printf("ok   %-18s refused as expected\n", c.name)
			continue
		}
		if host != c.wantHost || port != c.wantPort {
			fmt.Printf("FAIL %-18s SplitHostPort(%q) = %q, %d; want %q, %d\n",
				c.name, c.in, host, port, c.wantHost, c.wantPort)
			failures++
			continue
		}
		fmt.Printf("ok   %-18s %q %d\n", c.name, host, port)
	}
	fmt.Printf("%d case(s), %d failure(s)\n", len(cases), failures)

	// A deliberately wrong expectation, to show what a useful failure message
	// contains: the input, what came back, and what was wanted.
	_, port, _ := SplitHostPort("example.com:8080")
	want := 8000
	if port != want {
		fmt.Printf("FAIL alt: port = %d, want %d\n", port, want)
	}
}
