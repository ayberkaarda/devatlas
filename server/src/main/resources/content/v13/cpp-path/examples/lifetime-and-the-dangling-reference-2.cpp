// Returning by value is the repair; returning a reference needs a referent that outlives the call.
#include <cstddef>
#include <print>
#include <string>
#include <string_view>
#include <vector>

// Wrong shape: `const std::string&`. Right shape: an owned string, because the
// result must not depend on either argument still existing.
std::string longerOf(std::string_view left, std::string_view right) {
    return std::string{left.size() >= right.size() ? left : right};
}

// A reference return is correct here: the referent belongs to the caller and
// outlives the call by construction.
const std::string& firstOf(const std::vector<std::string>& entries) {
    return entries.front();
}

// A reference return is also correct here: an object with static storage duration
// lives until the program ends.
int& callCount() {
    static int count = 0;
    return count;
}

int main() {
    std::println("longer of two temporaries: {}", longerOf(std::string("alpha"), std::string("be")));

    const std::vector<std::string> entries{"first", "second"};
    const std::string& borrowed = firstOf(entries);
    std::println("borrowed from the caller's vector: {}", borrowed);
    std::println("it is the caller's element: {}", &borrowed == &entries.front());

    ++callCount();
    ++callCount();
    std::println("static storage survives the call: {}", callCount());

    // A view is safe exactly while the characters it points at are alive and
    // have not been moved. Growing the owner may move them, so the view is
    // re-seated rather than reused.
    std::string owned = "the quick brown fox";
    std::string_view view = owned;
    std::println("view size before growth: {}", view.size());
    owned += " jumps over";
    view = owned;
    std::println("view size after re-seating: {}", view.size());
    std::println("view still describes the owner: {}", view.size() == owned.size());
}
