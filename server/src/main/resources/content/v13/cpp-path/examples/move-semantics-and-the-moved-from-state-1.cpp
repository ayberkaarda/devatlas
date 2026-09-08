// `noexcept` on a move constructor is not decoration: the container reads it.
#include <print>
#include <string>
#include <utility>
#include <vector>

struct Cheap {
    static inline int copies = 0;
    static inline int moves = 0;

    std::string data;

    explicit Cheap(std::string text) : data(std::move(text)) {}
    Cheap(const Cheap& other) : data(other.data) { ++copies; }
    Cheap(Cheap&& other) noexcept : data(std::move(other.data)) { ++moves; }
    Cheap& operator=(const Cheap& other) {
        data = other.data;
        ++copies;
        return *this;
    }
    Cheap& operator=(Cheap&& other) noexcept {
        data = std::move(other.data);
        ++moves;
        return *this;
    }
    ~Cheap() = default;

    static void reset() {
        copies = 0;
        moves = 0;
    }
};

// The same type with one keyword removed: this move constructor may throw.
struct Risky {
    static inline int copies = 0;
    static inline int moves = 0;

    std::string data;

    explicit Risky(std::string text) : data(std::move(text)) {}
    Risky(const Risky& other) : data(other.data) { ++copies; }
    Risky(Risky&& other) : data(std::move(other.data)) { ++moves; }
    Risky& operator=(const Risky& other) {
        data = other.data;
        ++copies;
        return *this;
    }
    Risky& operator=(Risky&& other) {
        data = std::move(other.data);
        ++moves;
        return *this;
    }
    ~Risky() = default;

    static void reset() {
        copies = 0;
        moves = 0;
    }
};

int main() {
    std::vector<Cheap> cheap;
    cheap.reserve(3);
    cheap.emplace_back("one");
    cheap.emplace_back("two");
    cheap.emplace_back("three");

    Cheap::reset();
    cheap.reserve(cheap.capacity() + 1);  // must reallocate and relocate all elements
    std::println("nothrow move: elements={} relocated by move={} by copy={}", cheap.size(),
                 Cheap::moves, Cheap::copies);

    std::vector<Risky> risky;
    risky.reserve(3);
    risky.emplace_back("one");
    risky.emplace_back("two");
    risky.emplace_back("three");

    Risky::reset();
    risky.reserve(risky.capacity() + 1);
    std::println("throwing move: elements={} relocated by move={} by copy={}", risky.size(),
                 Risky::moves, Risky::copies);

    std::println("the container preserved every element: {}",
                 cheap.back().data == "three" && risky.back().data == "three");
}
