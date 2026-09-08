// Counting what a call costs: a type that reports every copy and every move.
#include <cstddef>
#include <print>
#include <string>
#include <utility>

struct Counted {
    static inline int copies = 0;
    static inline int moves = 0;

    std::string label;

    explicit Counted(std::string text) : label(std::move(text)) {}
    Counted(const Counted& other) : label(other.label) { ++copies; }
    Counted(Counted&& other) noexcept : label(std::move(other.label)) { ++moves; }
    Counted& operator=(const Counted& other) {
        label = other.label;
        ++copies;
        return *this;
    }
    Counted& operator=(Counted&& other) noexcept {
        label = std::move(other.label);
        ++moves;
        return *this;
    }
    ~Counted() = default;

    static void reset() {
        copies = 0;
        moves = 0;
    }
};

// The parameter is an independent object: the caller's argument is copied into it.
std::size_t byValue(Counted item) {
    return item.label.size();
}

// The parameter names the caller's object: nothing is constructed.
std::size_t byConstReference(const Counted& item) {
    return item.label.size();
}

// The parameter names the caller's object and may write through it.
void appendMark(Counted& item) {
    item.label += '!';
}

int main() {
    Counted subject{"a string long enough to allocate"};

    Counted::reset();
    const std::size_t a = byValue(subject);
    std::println("by value        size={} copies={} moves={}", a, Counted::copies, Counted::moves);

    Counted::reset();
    const std::size_t b = byConstReference(subject);
    std::println("by const&       size={} copies={} moves={}", b, Counted::copies, Counted::moves);

    Counted::reset();
    appendMark(subject);
    std::println("by &            size={} copies={} moves={}", subject.label.size(),
                 Counted::copies, Counted::moves);

    // An rvalue argument still initialises the by-value parameter, but by moving.
    Counted::reset();
    const std::size_t c = byValue(std::move(subject));
    std::println("by value (move) size={} copies={} moves={}", c, Counted::copies, Counted::moves);
}
