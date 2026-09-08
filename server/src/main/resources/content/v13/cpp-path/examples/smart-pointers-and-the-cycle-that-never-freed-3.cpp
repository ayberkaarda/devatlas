// Two shared_ptrs pointing at each other keep each other alive for ever.
#include <memory>
#include <print>
#include <string>
#include <utility>

// Strong in both directions.
struct Node {
    static inline int destroyed = 0;

    std::string name;
    std::shared_ptr<Node> peer;

    explicit Node(std::string label) : name(std::move(label)) {}
    ~Node() { ++destroyed; }
};

// The same structure with one direction weakened.
struct Watcher {
    static inline int destroyed = 0;

    std::string name;
    std::weak_ptr<Watcher> peer;

    explicit Watcher(std::string label) : name(std::move(label)) {}
    ~Watcher() { ++destroyed; }
};

int main() {
    {
        std::shared_ptr<Node> left = std::make_shared<Node>("left");
        std::shared_ptr<Node> right = std::make_shared<Node>("right");
        left->peer = right;
        right->peer = left;
        std::println("in scope:  left use_count={} right use_count={}", left.use_count(),
                     right.use_count());
        std::println("reachable: {} and {}", left->peer->name, right->peer->name);
    }
    std::println("out of scope: nodes destroyed={}", Node::destroyed);
    std::println("the cycle outlived its owners: {}", Node::destroyed == 0);

    {
        std::shared_ptr<Watcher> left = std::make_shared<Watcher>("left");
        std::shared_ptr<Watcher> right = std::make_shared<Watcher>("right");
        left->peer = right;
        right->peer = left;
        std::println("in scope:  left use_count={} right use_count={}", left.use_count(),
                     right.use_count());
        std::println("reachable through a weak_ptr: {}", left->peer.lock() != nullptr);
    }
    std::println("out of scope: watchers destroyed={}", Watcher::destroyed);
    std::println("breaking the cycle freed both: {}", Watcher::destroyed == 2);
}
