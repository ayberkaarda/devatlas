// Copy elision: the copies the standard forbids, and the move it still requires.
#include <print>
#include <string>
#include <utility>
#include <vector>

struct Counted {
    static inline int copies = 0;
    static inline int moves = 0;

    std::string label;

    explicit Counted(std::string text) : label(std::move(text)) {}
    Counted(const Counted& other) : label(other.label) { ++copies; }
    Counted(Counted&& other) noexcept : label(std::move(other.label)) { ++moves; }
    Counted& operator=(const Counted&) = delete;
    Counted& operator=(Counted&&) = delete;
    ~Counted() = default;

    static void reset() {
        copies = 0;
        moves = 0;
    }
};

// The returned expression is a prvalue of the function's own return type, so the
// result object the caller supplies is the object this expression initialises.
Counted makeCounted(const char* text) {
    return Counted{text};
}

// The returned expression is an lvalue, so a constructor really is called here.
Counted passThrough(Counted taken) {
    return taken;
}

int main() {
    std::vector<Counted> box;
    box.reserve(2);

    Counted::reset();
    Counted direct = Counted{"initialised from a prvalue"};
    std::println("prvalue initialiser  len={} copies={} moves={}", direct.label.size(),
                 Counted::copies, Counted::moves);

    Counted::reset();
    Counted returned = makeCounted("returned as a prvalue");
    std::println("prvalue from a call  len={} copies={} moves={}", returned.label.size(),
                 Counted::copies, Counted::moves);

    Counted::reset();
    box.push_back(Counted{"pushed"});
    std::println("push_back(prvalue)   len={} copies={} moves={}", box.back().label.size(),
                 Counted::copies, Counted::moves);

    Counted::reset();
    box.emplace_back("emplaced in place");
    std::println("emplace_back(args)   len={} copies={} moves={}", box.back().label.size(),
                 Counted::copies, Counted::moves);

    // A named parameter returned by name is an lvalue in a context the standard
    // does not make an elision guarantee about, but overload resolution treats
    // it as an rvalue first, so the move constructor is the one selected.
    Counted::reset();
    Counted forwarded = passThrough(Counted{"through a parameter"});
    std::println("returning a parameter is a move: {}", Counted::copies == 0 && Counted::moves >= 1);
    std::println("its length survived: {}", forwarded.label.size());
}
