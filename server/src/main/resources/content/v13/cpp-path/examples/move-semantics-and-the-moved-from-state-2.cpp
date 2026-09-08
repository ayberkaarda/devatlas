// std::move performs no move: it is a cast that changes which overload is chosen.
#include <print>
#include <string>
#include <utility>

struct Payload {
    static inline int copies = 0;
    static inline int moves = 0;

    std::string data;

    explicit Payload(std::string text) : data(std::move(text)) {}
    Payload(const Payload& other) : data(other.data) { ++copies; }
    Payload(Payload&& other) noexcept : data(std::move(other.data)) { ++moves; }
    Payload& operator=(const Payload& other) {
        data = other.data;
        ++copies;
        return *this;
    }
    Payload& operator=(Payload&& other) noexcept {
        data = std::move(other.data);
        ++moves;
        return *this;
    }
    ~Payload() = default;

    static void reset() {
        copies = 0;
        moves = 0;
    }
};

// The sink idiom: take by value, then move into the member. One overload serves
// callers that own their argument and callers that do not.
struct Envelope {
    explicit Envelope(Payload content) : content_(std::move(content)) {}
    std::size_t size() const { return content_.data.size(); }

private:
    Payload content_;
};

int main() {
    Payload source{"a payload worth moving"};

    Payload::reset();
    Payload moved{std::move(source)};
    std::println("moving an lvalue:      copies={} moves={}", Payload::copies, Payload::moves);

    const Payload frozen{"a const payload"};
    Payload::reset();
    Payload fromConst{std::move(frozen)};
    std::println("moving a const lvalue: copies={} moves={}", Payload::copies, Payload::moves);
    std::println("  the const object is intact: len={}", frozen.data.size());

    Payload::reset();
    (void)std::move(fromConst);
    std::println("std::move on its own:  copies={} moves={}", Payload::copies, Payload::moves);
    std::println("  the object is untouched: len={}", fromConst.data.size());

    Payload::reset();
    const Envelope fromRvalue{Payload{"built for the call"}};
    std::println("sink from an rvalue:   copies={} moves={} size={}", Payload::copies,
                 Payload::moves, fromRvalue.size());

    Payload::reset();
    const Envelope fromLvalue{moved};
    std::println("sink from an lvalue:   copies={} moves={} size={}", Payload::copies,
                 Payload::moves, fromLvalue.size());
}
