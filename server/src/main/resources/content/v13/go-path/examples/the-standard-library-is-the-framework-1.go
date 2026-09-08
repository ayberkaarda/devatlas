// A router, a handler and a server, using nothing but net/http. The patterns
// carry a method and named wildcards, which http.ServeMux has understood since
// Go 1.22, so the routing a small service needs is already in the standard
// library.

package main

import (
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"sort"
	"strings"
)

type store map[string]string

func (s store) routes() *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /items/{id}", s.get)
	mux.HandleFunc("PUT /items/{id}", s.put)
	mux.HandleFunc("GET /items", s.list)
	return mux
}

func (s store) get(w http.ResponseWriter, r *http.Request) {
	// PathValue reads a wildcard the pattern captured. No parsing by hand.
	id := r.PathValue("id")
	value, ok := s[id]
	if !ok {
		http.Error(w, "no such item", http.StatusNotFound)
		return
	}
	fmt.Fprintf(w, "%s=%s", id, value)
}

func (s store) put(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	s[id] = r.URL.Query().Get("value")
	w.WriteHeader(http.StatusCreated)
	fmt.Fprintf(w, "stored %s", id)
}

func (s store) list(w http.ResponseWriter, r *http.Request) {
	// Ranging a map would emit the keys in a random order, so they are sorted
	// before anything is written.
	keys := make([]string, 0, len(s))
	for k := range s {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	fmt.Fprint(w, strings.Join(keys, ","))
}

func main() {
	// httptest.NewServer starts a real server on a loopback port of its own
	// choosing. The port is never printed, because it differs on every run.
	server := httptest.NewServer(store{}.routes())
	defer server.Close()

	// Only bodies this program wrote are printed. The router's own 404 and
	// 405 texts are the library's wording, so those lines report the status
	// code alone.
	do := func(method, path string) {
		request, err := http.NewRequest(method, server.URL+path, nil)
		if err != nil {
			fmt.Println("request error:", err)
			return
		}
		response, err := http.DefaultClient.Do(request)
		if err != nil {
			fmt.Println("transport error:", err)
			return
		}
		defer response.Body.Close()
		body, _ := io.ReadAll(response.Body)
		line := fmt.Sprintf("%-6s %-22s -> %d", method, path, response.StatusCode)
		if response.StatusCode < 400 {
			line += " " + string(body)
		}
		fmt.Println(line)
	}

	do("GET", "/items/alpha")
	do("PUT", "/items/alpha?value=one")
	do("PUT", "/items/beta?value=two")
	do("GET", "/items/alpha")
	do("GET", "/items")
	do("DELETE", "/items/alpha")
	do("GET", "/nowhere")

	// The same handler, exercised without a socket. httptest.NewRecorder is
	// an http.ResponseWriter that keeps what was written, which is how a
	// handler is unit tested.
	recorder := httptest.NewRecorder()
	seeded := store{"gamma": "three"}
	seeded.routes().ServeHTTP(recorder, httptest.NewRequest("GET", "/items/gamma", nil))
	fmt.Printf("recorder -> %d %s\n", recorder.Code, recorder.Body.String())
	fmt.Printf("content type header set by net/http: %q\n",
		recorder.Result().Header.Get("Content-Type"))
}
