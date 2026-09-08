// A constraint written at the declaration, checked at the call.
#include <concepts>
#include <print>
#include <string>

template <typename T>
    requires std::integral<T>
T doubled(T value) {
    return static_cast<T>(value * 2);
}

// The same name for a disjoint set of types. No ambiguity, because no type is
// both integral and floating-point.
template <std::floating_point T>
T doubled(T value) {
    return value * 2;
}

// A requires-expression is also a question you can ask in ordinary code: is this
// call well formed?
template <typename T>
concept Doublable = requires(T value) { doubled(value); };

int main() {
    std::println("doubled(21)        = {}", doubled(21));
    std::println("doubled(2.5)       = {}", doubled(2.5));
    std::println("doubled(1000000LL) = {}", doubled(1000000LL));

    std::println("int is Doublable:         {}", Doublable<int>);
    std::println("double is Doublable:      {}", Doublable<double>);
    std::println("std::string is Doublable: {}", Doublable<std::string>);

    static_assert(Doublable<int>);
    static_assert(!Doublable<std::string>);
}
