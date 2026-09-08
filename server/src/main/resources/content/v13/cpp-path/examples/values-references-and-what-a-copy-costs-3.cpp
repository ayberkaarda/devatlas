// A reference is another name for an object, not a small object that holds an address.
#include <print>
#include <string>

struct Tag {
    std::string name;
};

// Returning a reference to a parameter is safe exactly as long as the caller's
// object outlives the call, which here it does.
const Tag& shorterName(const Tag& left, const Tag& right) {
    return left.name.size() <= right.name.size() ? left : right;
}

int main() {
    int value = 10;
    int& alias = value;
    std::println("alias names the same object: {}", &alias == &value);

    alias = 20;
    std::println("value after writing through the alias: {}", value);

    int other = 99;
    alias = other;  // an assignment to `value`, not a re-seating of `alias`
    std::println("value after assigning `other` through the alias: {}", value);
    std::println("alias still names the first object: {}", &alias == &value);
    std::println("other is unchanged: {}", other);

    // Binding a temporary to a const reference extends that temporary's lifetime
    // to the lifetime of the reference.
    const std::string& borrowed = std::string("bound to a const reference");
    std::println("the temporary is still readable: len={}", borrowed.size());

    const Tag first{"first"};
    const Tag second{"second-longest"};
    const Tag& chosen = shorterName(first, second);
    std::println("chosen name: {}", chosen.name);
    std::println("chosen is the caller's object: {}", &chosen == &first);
}
