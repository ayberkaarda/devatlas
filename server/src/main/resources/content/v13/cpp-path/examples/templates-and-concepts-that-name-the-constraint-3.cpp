// The more constrained overload wins, and `if constexpr` prunes the branch not taken.
#include <concepts>
#include <print>
#include <string>
#include <type_traits>

template <typename T>
concept Number = std::integral<T> || std::floating_point<T>;

// Strictly more constrained: everything SignedNumber requires, and one clause more.
template <typename T>
concept SignedNumber = Number<T> && std::is_signed_v<T>;

template <Number T>
std::string kind(T) {
    return "number";
}

template <SignedNumber T>
std::string kind(T) {
    return "signed number";
}

// One function body, two type-dependent shapes. The branch not taken is not
// compiled for that instantiation, so `value ? ... : ...` never has to make sense
// for a std::string.
template <typename T>
std::string render(const T& value) {
    if constexpr (std::same_as<T, bool>) {
        return value ? "yes" : "no";
    } else if constexpr (std::integral<T>) {
        return std::to_string(value);
    } else {
        return std::string{value};
    }
}

template <typename... Ts>
auto total(Ts... values) {
    return (values + ... + 0);
}

int main() {
    std::println("kind(1)            = {}", kind(1));
    std::println("kind(1u)           = {}", kind(1u));
    std::println("kind(1.5)          = {}", kind(1.5));

    std::println("render(true)       = {}", render(true));
    std::println("render(42)         = {}", render(42));
    std::println("render(\"literal\")  = {}", render("literal"));

    std::println("total()            = {}", total());
    std::println("total(1, 2, 3, 4)  = {}", total(1, 2, 3, 4));
    std::println("total(1.5, 2.5)    = {}", total(1.5, 2.5));
}
