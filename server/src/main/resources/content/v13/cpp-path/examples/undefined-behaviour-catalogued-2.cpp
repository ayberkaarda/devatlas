// Nothing indeterminate: the initialisation forms that leave no value unset.
#include <array>
#include <print>
#include <string>
#include <vector>

struct Config {
    int retries = 3;  // a default member initialiser applies to every constructor
    bool verbose = false;
    std::string name;  // a class type is constructed whatever you do
};

// No initialisers. Default-initialising one of these leaves the two scalars
// indeterminate; value-initialising it zeroes them.
struct Raw {
    int count;
    double weight;
};

int sumOf(const std::vector<int>& values) {
    int total = 0;
    for (const int value : values) {
        total += value;
    }
    return total;
}

int main() {
    const int valueInitialised{};
    const int alsoZero = int();
    std::println("value-initialised scalars: {} and {}", valueInitialised, alsoZero);

    const Config config{};
    std::println("defaults applied: retries={} verbose={} name empty={}", config.retries,
                 config.verbose, config.name.empty());

    const Config overridden{.retries = 5, .verbose = true, .name = "run"};
    std::println("designated initialisers: retries={} verbose={} name={}", overridden.retries,
                 overridden.verbose, overridden.name);

    const Raw zeroed{};
    std::println("value-initialised aggregate: count={} weight={}", zeroed.count, zeroed.weight);

    const std::array<int, 4> array{};
    const std::vector<int> vector(4);
    std::println("array and vector elements start at zero: {} and {}",
                 array[0] + array[1] + array[2] + array[3], sumOf(vector));

    const std::string text;
    std::println("a default-constructed string is empty: {}", text.empty());
}
