// The fixture that lies. A fixture built once and shared by every case makes
// the cases depend on the order they ran in, and the suite goes green or red
// according to something nobody wrote down. Building the fixture per case
// costs a few allocations and removes the whole class of bug.

package main

import (
	"fmt"
	"sort"
)

type Cart struct {
	items map[string]int
}

func newCart() *Cart { return &Cart{items: map[string]int{}} }

func (c *Cart) Add(sku string, qty int) { c.items[sku] += qty }

func (c *Cart) Count() int {
	total := 0
	for _, n := range c.items {
		total += n
	}
	return total
}

// Contents returns the SKUs in a fixed order. Ranging the map and returning
// what came out would make every caller's output depend on the map's
// randomised iteration order.
func (c *Cart) Contents() []string {
	skus := make([]string, 0, len(c.items))
	for sku := range c.items {
		skus = append(skus, sku)
	}
	sort.Strings(skus)
	return skus
}

type check struct {
	name string
	add  map[string]int
	want int
}

func main() {
	checks := []check{
		{name: "one line", add: map[string]int{"a": 1}, want: 1},
		{name: "two lines", add: map[string]int{"a": 1, "b": 2}, want: 3},
		{name: "empty cart", add: nil, want: 0},
	}

	// Shared fixture: every case adds to the same cart, so the counts only
	// line up if the cases run in one particular order -- and the third case
	// cannot pass at all.
	shared := newCart()
	fmt.Println("shared fixture:")
	for _, c := range checks {
		for sku, qty := range c.add {
			shared.Add(sku, qty)
		}
		got := shared.Count()
		fmt.Printf("  %-11s got %d want %d -> %s\n", c.name, got, c.want, verdict(got == c.want))
	}
	fmt.Printf("  cart ended up holding %v\n", shared.Contents())

	// Fresh fixture: each case builds what it needs and nothing survives it.
	fmt.Println("fresh fixture per case:")
	for _, c := range checks {
		cart := newCart()
		for sku, qty := range c.add {
			cart.Add(sku, qty)
		}
		got := cart.Count()
		fmt.Printf("  %-11s got %d want %d -> %s\n", c.name, got, c.want, verdict(got == c.want))
	}

	// Reversing the order changes nothing now, which is the property a suite
	// needs: a case's result must not depend on its neighbours.
	fmt.Println("fresh fixture, cases reversed:")
	for i := len(checks) - 1; i >= 0; i-- {
		c := checks[i]
		cart := newCart()
		for sku, qty := range c.add {
			cart.Add(sku, qty)
		}
		got := cart.Count()
		fmt.Printf("  %-11s got %d want %d -> %s\n", c.name, got, c.want, verdict(got == c.want))
	}
}

func verdict(ok bool) string {
	if ok {
		return "pass"
	}
	return "FAIL"
}
