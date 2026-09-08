// encoding/json is the other half of a small service: struct tags describe the
// wire shape, the decoder refuses what it does not recognise, and the error
// contract is a struct like any other.

package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
)

type Item struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Price int    `json:"price_cents"`
	Notes string `json:"notes,omitempty"`
}

// APIError is the one body shape every failure uses, so a client never has to
// parse prose.
type APIError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	encoder := json.NewEncoder(w)
	encoder.SetIndent("", "")
	if err := encoder.Encode(payload); err != nil {
		fmt.Println("encode failed:", err)
	}
}

func create(w http.ResponseWriter, r *http.Request) {
	decoder := json.NewDecoder(r.Body)
	// Without this a misspelled field is silently dropped and the caller is
	// told nothing.
	decoder.DisallowUnknownFields()

	var item Item
	if err := decoder.Decode(&item); err != nil {
		var typeErr *json.UnmarshalTypeError
		switch {
		case errors.As(err, &typeErr):
			writeJSON(w, http.StatusBadRequest, APIError{
				Code:    "WRONG_TYPE",
				Message: "field " + typeErr.Field + " has the wrong type",
			})
		default:
			writeJSON(w, http.StatusBadRequest, APIError{
				Code:    "MALFORMED_BODY",
				Message: "the body could not be decoded",
			})
		}
		return
	}
	if item.Name == "" {
		writeJSON(w, http.StatusUnprocessableEntity, APIError{
			Code:    "NAME_REQUIRED",
			Message: "name must not be empty",
		})
		return
	}
	writeJSON(w, http.StatusCreated, item)
}

func main() {
	handler := http.HandlerFunc(create)

	post := func(label, body string) {
		recorder := httptest.NewRecorder()
		request := httptest.NewRequest("POST", "/items", strings.NewReader(body))
		handler.ServeHTTP(recorder, request)
		fmt.Printf("%-16s %d %s", label, recorder.Code, recorder.Body.String())
	}

	post("valid", `{"id":"a-1","name":"Lamp","price_cents":1999}`)
	post("unknown field", `{"id":"a-2","name":"Lamp","colour":"red"}`)
	post("wrong type", `{"id":"a-3","name":"Lamp","price_cents":"free"}`)
	post("not json", `{`)
	post("missing name", `{"id":"a-4"}`)

	// Marshalling is the mirror image. omitempty drops the zero value, and
	// map keys are sorted by the encoder, which is what makes this output
	// stable.
	blob, _ := json.Marshal(Item{ID: "a-9", Name: "Desk", Price: 12500})
	fmt.Printf("marshalled: %s\n", blob)

	counts := map[string]int{"zeta": 1, "alpha": 2, "mu": 3}
	sorted, _ := json.Marshal(counts)
	fmt.Printf("map keys sorted by the encoder: %s\n", sorted)

	// Decoding into a struct ignores JSON fields the struct does not name,
	// unless DisallowUnknownFields was set.
	var relaxed Item
	_ = json.Unmarshal([]byte(`{"id":"a-5","name":"Chair","unmapped":true}`), &relaxed)
	fmt.Printf("relaxed decode: %+v\n", relaxed)
}
