// The rule of zero, and a scope guard for a resource that has no class of its own.
#include <print>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

// Every member manages itself, so this class declares no destructor, no copy
// operations and no move operations. The compiler's are correct.
struct Session {
    std::string user;
    std::vector<std::string> events;
};

// Some resources are a flag, a counter or a callback registration. A guard turns
// "undo this later" into "undo this when the scope ends".
template <typename Action>
class Finally {
public:
    explicit Finally(Action action) : action_(std::move(action)) {}
    ~Finally() { action_(); }

    Finally(const Finally&) = delete;
    Finally& operator=(const Finally&) = delete;
    Finally(Finally&&) = delete;
    Finally& operator=(Finally&&) = delete;

private:
    Action action_;
};

int activeRequests = 0;

void handleRequest(bool rejectEarly) {
    ++activeRequests;
    const Finally release{[] { --activeRequests; }};

    if (rejectEarly) {
        std::println("  rejected, active={}", activeRequests);
        return;
    }
    std::println("  handled, active={}", activeRequests);
}

int main() {
    static_assert(std::is_copy_constructible_v<Session>);
    static_assert(std::is_nothrow_move_constructible_v<Session>);

    Session original{"ada", {"login"}};
    Session copy = original;
    copy.events.emplace_back("logout");

    std::println("the copy is independent: {}", original.events.size() != copy.events.size());
    std::println("original events: {}, copy events: {}", original.events.size(),
                 copy.events.size());
    std::println("Session declares no special members: {}",
                 std::is_copy_assignable_v<Session> && std::is_move_assignable_v<Session>);

    std::println("two requests, one rejected:");
    handleRequest(false);
    handleRequest(true);
    std::println("active after both: {}", activeRequests);
}
