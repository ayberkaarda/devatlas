// A concept is a named predicate over types, and it is testable like any other value.
#include <concepts>
#include <format>
#include <print>
#include <string>
#include <string_view>

// Two requirements, named separately so that each can be asked about on its own.
template <typename T>
concept HasName = requires(const T& value) {
    { value.name() } -> std::convertible_to<std::string_view>;
};

template <typename T>
concept HasIntegerId = requires(const T& value) {
    { value.id() } -> std::same_as<int>;
};

template <typename T>
concept Named = HasName<T> && HasIntegerId<T>;

struct User {
    std::string label;
    int number;

    std::string_view name() const { return label; }
    int id() const { return number; }
};

// Satisfies neither requirement: no name(), and id() returns the wrong type.
struct Blob {
    int bytes;

    long id() const { return bytes; }
};

template <Named T>
std::string describe(const T& value) {
    return std::format("{}#{}", value.name(), value.id());
}

int main() {
    std::println("User satisfies Named: {}", Named<User>);
    std::println("Blob satisfies Named: {}", Named<Blob>);

    const User user{"ada", 7};
    std::println("describe(user) = {}", describe(user));

    // Because the clauses are named, the failure can be located rather than
    // guessed at: a concept is a value, and values can be printed.
    std::println("Blob has a usable name():    {}", HasName<Blob>);
    std::println("Blob has an int-valued id(): {}", HasIntegerId<Blob>);

    static_assert(Named<User>);
    static_assert(!Named<Blob>);
}
