// A type that owns a releasable resource declares what copying and moving mean.
#include <print>
#include <string>
#include <utility>

struct Ledger {
    static inline int open = 0;
    static inline int released = 0;
};

class Connection {
public:
    explicit Connection(std::string name) : name_(std::move(name)), owns_(true) { ++Ledger::open; }

    ~Connection() { release(); }

    // Copying would give two objects one resource to release. Say so.
    Connection(const Connection&) = delete;
    Connection& operator=(const Connection&) = delete;

    // Moving transfers the obligation and leaves the source owning nothing.
    Connection(Connection&& other) noexcept
        : name_(std::move(other.name_)), owns_(std::exchange(other.owns_, false)) {}

    Connection& operator=(Connection&& other) noexcept {
        if (this != &other) {
            release();
            name_ = std::move(other.name_);
            owns_ = std::exchange(other.owns_, false);
        }
        return *this;
    }

    bool owns() const { return owns_; }
    const std::string& name() const { return name_; }

private:
    void release() {
        if (owns_) {
            --Ledger::open;
            ++Ledger::released;
            owns_ = false;
        }
    }

    std::string name_;
    bool owns_;
};

int main() {
    {
        Connection primary{"primary"};
        std::println("after construction: open={} primary owns={}", Ledger::open, primary.owns());

        Connection adopted{std::move(primary)};
        std::println("after the move:     open={} source owns={} target owns={}", Ledger::open,
                     primary.owns(), adopted.owns());
        std::println("the name travelled: {}", adopted.name());

        Connection secondary{"secondary"};
        std::println("two live handles:   open={}", Ledger::open);

        secondary = std::move(adopted);
        std::println("after move-assign:  open={} released={}", Ledger::open, Ledger::released);
        std::println("the survivor is:    {}", secondary.name());
    }

    std::println("after the block:    open={} released={}", Ledger::open, Ledger::released);
}
