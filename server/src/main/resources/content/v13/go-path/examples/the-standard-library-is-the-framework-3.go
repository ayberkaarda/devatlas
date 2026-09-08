// Middleware in net/http is a function from http.Handler to http.Handler.
// That one signature is the whole extension mechanism, and context.Context is
// how a request carries values and cancellation through it.

package main

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
)

// A private key type keeps this package's context values from colliding with
// any other package's.
type ctxKey int

const userKey ctxKey = iota

// requireToken rejects the request or attaches the caller's identity and
// passes it on. It returns a handler, so it composes with anything.
func requireToken(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		token := r.Header.Get("Authorization")
		user, ok := strings.CutPrefix(token, "Bearer ")
		if !ok || user == "" {
			http.Error(w, "unauthorised", http.StatusUnauthorized)
			return
		}
		ctx := context.WithValue(r.Context(), userKey, user)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// audit records what happened. It writes to a slice rather than to a clock, so
// nothing in the output depends on how long anything took.
func audit(log *[]string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			*log = append(*log, r.Method+" "+r.URL.Path)
			next.ServeHTTP(w, r)
		})
	}
}

func chain(h http.Handler, wrappers ...func(http.Handler) http.Handler) http.Handler {
	// Applied in reverse so that the first wrapper listed is the outermost.
	for i := len(wrappers) - 1; i >= 0; i-- {
		h = wrappers[i](h)
	}
	return h
}

func whoami(w http.ResponseWriter, r *http.Request) {
	user, _ := r.Context().Value(userKey).(string)
	fmt.Fprintf(w, "hello %s", user)
}

// work respects cancellation: it stops as soon as the request's context is
// done rather than finishing and writing to a connection nobody is reading.
func work(ctx context.Context, steps int) (int, error) {
	total := 0
	for i := 1; i <= steps; i++ {
		select {
		case <-ctx.Done():
			return total, ctx.Err()
		default:
		}
		total += i
	}
	return total, nil
}

func main() {
	var log []string
	handler := chain(http.HandlerFunc(whoami), audit(&log), requireToken)

	call := func(label, token string) {
		recorder := httptest.NewRecorder()
		request := httptest.NewRequest("GET", "/whoami", nil)
		if token != "" {
			request.Header.Set("Authorization", token)
		}
		handler.ServeHTTP(recorder, request)
		line := fmt.Sprintf("%-13s %d", label, recorder.Code)
		if recorder.Code < 400 {
			line += " " + recorder.Body.String()
		}
		fmt.Println(line)
	}

	call("no token", "")
	call("wrong shape", "Basic abc")
	call("valid token", "Bearer ada")
	fmt.Printf("audit log: %v\n", log)

	// Cancellation, without a clock: the context is cancelled before the work
	// starts, so the first check stops it.
	cancelled, cancel := context.WithCancel(context.Background())
	cancel()
	total, err := work(cancelled, 100)
	fmt.Printf("cancelled run: total=%d canceled=%v\n", total, errors.Is(err, context.Canceled))

	total, err = work(context.Background(), 100)
	fmt.Printf("complete run:  total=%d err=%v\n", total, err)

	// context.WithValue is for request-scoped data that travels with the
	// request, and a private key type is what keeps two packages apart.
	ctx := context.WithValue(context.Background(), userKey, "ada")
	fmt.Printf("value out of the context: %v\n", ctx.Value(userKey))
	fmt.Printf("another package's key would miss: %v\n", ctx.Value(0))
}
