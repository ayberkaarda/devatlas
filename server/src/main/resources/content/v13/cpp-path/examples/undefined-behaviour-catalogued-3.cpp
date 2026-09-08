// Staying inside the object: bounds that travel with the data, and a defined pun.
#include <bit>
#include <cstdint>
#include <iterator>
#include <print>
#include <span>
#include <stdexcept>
#include <string>
#include <vector>

// A span carries the length with the pointer, so a callee cannot be handed a
// pointer and left to guess how far it may walk.
int sumOf(std::span<const int> values) {
    int total = 0;
    for (const int value : values) {
        total += value;
    }
    return total;
}

int main() {
    std::vector<int> values{10, 20, 30, 40};

    // at() checks the index against size(); operator[] checks nothing.
    bool threw = false;
    try {
        (void)values.at(9);
    } catch (const std::out_of_range&) {
        threw = true;
    }
    std::println("at(9) on a vector of {} threw: {}", values.size(), threw);
    std::println("at(2) returned: {}", values.at(2));

    std::println("span size={} sum={}", std::span<const int>{values}.size(), sumOf(values));

    // std::ssize gives a signed length, so counting down does not depend on an
    // unsigned subtraction staying above zero.
    std::string descending;
    for (std::ptrdiff_t i = std::ssize(values) - 1; i >= 0; --i) {
        if (!descending.empty()) {
            descending += ' ';
        }
        descending += std::to_string(values[static_cast<std::size_t>(i)]);
    }
    std::println("walked backwards with a signed index: {}", descending);

    // Reading an object through a pointer to an unrelated type is undefined.
    // bit_cast copies the bytes into a new object of the target type, which is not.
    constexpr float one = 1.0F;
    const auto bits = std::bit_cast<std::uint32_t>(one);
    const auto restored = std::bit_cast<float>(bits);
    std::println("bit_cast round trip preserved the value: {}", restored == one);
    std::println("bit_cast requires equal sizes, so this compiled: {}",
                 sizeof(float) == sizeof(std::uint32_t));
}
