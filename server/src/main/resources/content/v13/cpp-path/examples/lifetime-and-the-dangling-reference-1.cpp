// When objects die: end of full-expression, end of block, and the one extension.
#include <print>
#include <string>
#include <utility>

struct Tracer {
    std::string name;

    explicit Tracer(std::string label) : name(std::move(label)) {
        std::println("  constructed {}", name);
    }
    ~Tracer() { std::println("  destroyed   {}", name); }

    Tracer(const Tracer&) = delete;
    Tracer& operator=(const Tracer&) = delete;
    Tracer(Tracer&&) = delete;
    Tracer& operator=(Tracer&&) = delete;
};

// The returned expression is a prvalue of the return type, so no copy or move is
// needed and the deleted ones are never considered.
Tracer make(const char* label) {
    return Tracer{label};
}

int main() {
    std::println("a temporary used and discarded:");
    std::println("  length is {}", make("temporary").name.size());
    std::println("the semicolon has passed");

    std::println("a temporary bound to a const reference:");
    {
        const Tracer& kept = make("extended");
        std::println("  read after the semicolon: {}", kept.name);
    }
    std::println("the block has ended");

    std::println("an ordinary automatic object:");
    {
        const Tracer first{"first"};
        const Tracer second{"second"};
        std::println("  both alive: {} and {}", first.name, second.name);
    }
    std::println("the block has ended");
}
