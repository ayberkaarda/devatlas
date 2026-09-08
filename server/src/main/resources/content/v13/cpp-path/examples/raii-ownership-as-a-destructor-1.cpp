// A destructor is the release half of the acquisition, and it runs on every exit path.
#include <print>
#include <stdexcept>
#include <string>
#include <utility>

// A stand-in for an external resource that must be handed back.
struct Ledger {
    static inline int open = 0;
    static inline int acquired = 0;
    static inline int released = 0;
};

class ScopedConnection {
public:
    explicit ScopedConnection(std::string name) : name_(std::move(name)) {
        ++Ledger::open;
        ++Ledger::acquired;
        std::println("  open  {} (open now {})", name_, Ledger::open);
    }

    ~ScopedConnection() {
        --Ledger::open;
        ++Ledger::released;
        std::println("  close {} (open now {})", name_, Ledger::open);
    }

    ScopedConnection(const ScopedConnection&) = delete;
    ScopedConnection& operator=(const ScopedConnection&) = delete;
    ScopedConnection(ScopedConnection&&) = delete;
    ScopedConnection& operator=(ScopedConnection&&) = delete;

    const std::string& name() const { return name_; }

private:
    std::string name_;
};

bool tooShort(const ScopedConnection& connection) {
    return connection.name().size() < 4;
}

void withEarlyReturn(std::string name) {
    const ScopedConnection probe{std::move(name)};
    if (tooShort(probe)) {
        std::println("  name too short, returning early");
        return;
    }
    std::println("  past the guard");
}

void withThrow() {
    const ScopedConnection outer{"outer"};
    const ScopedConnection inner{"inner"};
    throw std::runtime_error("the operation failed");
}

int main() {
    std::println("plain block, two connections:");
    {
        const ScopedConnection first{"first"};
        const ScopedConnection second{"second"};
        std::println("  both usable: {} and {}", first.name(), second.name());
    }

    std::println("a function that returns early:");
    withEarlyReturn("ok");
    withEarlyReturn("probe");

    std::println("a function that throws:");
    try {
        withThrow();
    } catch (const std::runtime_error&) {
        std::println("  caught after unwinding");
    }

    std::println("acquired={} released={} still open={}", Ledger::acquired, Ledger::released,
                 Ledger::open);
}
