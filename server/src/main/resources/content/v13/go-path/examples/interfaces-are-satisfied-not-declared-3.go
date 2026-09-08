// Because satisfaction is implicit, a type written today can plug into an
// interface the standard library declared years earlier. Here one local type
// satisfies sort.Interface, fmt.Stringer and error without importing any of
// them into its own declaration.

package main

import (
	"fmt"
	"io"
	"sort"
	"strings"
)

type Release struct {
	Name  string
	Major int
	Minor int
}

// String satisfies fmt.Stringer, which fmt consults for %v and %s.
func (r Release) String() string { return fmt.Sprintf("%s %d.%d", r.Name, r.Major, r.Minor) }

// Releases satisfies sort.Interface. The comparison is a total order -- major,
// then minor, then name -- so the sorted result is the same on every run.
type Releases []Release

func (rs Releases) Len() int      { return len(rs) }
func (rs Releases) Swap(i, j int) { rs[i], rs[j] = rs[j], rs[i] }
func (rs Releases) Less(i, j int) bool {
	if rs[i].Major != rs[j].Major {
		return rs[i].Major < rs[j].Major
	}
	if rs[i].Minor != rs[j].Minor {
		return rs[i].Minor < rs[j].Minor
	}
	return rs[i].Name < rs[j].Name
}

// UnsupportedError satisfies the predeclared error interface, which is an
// interface like any other.
type UnsupportedError struct{ Release Release }

func (e UnsupportedError) Error() string { return "unsupported: " + e.Release.String() }

func check(r Release) error {
	if r.Major < 1 {
		return UnsupportedError{Release: r}
	}
	return nil
}

// countLines accepts io.Reader rather than a concrete type, so any source of
// bytes will do. Accepting an interface and returning a concrete value is the
// usual direction.
func countLines(source io.Reader) (int, error) {
	data, err := io.ReadAll(source)
	if err != nil {
		return 0, err
	}
	return strings.Count(string(data), "\n"), nil
}

func main() {
	releases := Releases{
		{Name: "tool", Major: 2, Minor: 1},
		{Name: "tool", Major: 1, Minor: 9},
		{Name: "agent", Major: 2, Minor: 1},
		{Name: "agent", Major: 0, Minor: 4},
	}

	sort.Sort(releases)
	for _, r := range releases {
		fmt.Printf("%v\n", r)
	}

	for _, r := range releases {
		if err := check(r); err != nil {
			fmt.Printf("rejected: %v\n", err)
		}
	}

	// strings.Reader satisfies io.Reader; so does anything else that reads.
	n, err := countLines(strings.NewReader("one\ntwo\nthree\n"))
	fmt.Printf("lines=%d err=%v\n", n, err)

	// sort.IsSorted asks the same three methods, so it agrees with the order
	// Less defined rather than with any built-in notion of order.
	fmt.Printf("sorted according to Less: %v\n", sort.IsSorted(releases))
}
