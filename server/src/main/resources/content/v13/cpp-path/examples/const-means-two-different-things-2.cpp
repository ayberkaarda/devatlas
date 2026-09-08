// `const` on a member function: which overload runs, and what `mutable` buys.
#include <cstddef>
#include <print>
#include <string>
#include <utility>
#include <vector>

class Catalogue {
public:
    explicit Catalogue(std::vector<std::string> entries) : entries_(std::move(entries)) {}

    // Selected when the object is const. It promises not to change the observable
    // state, and `mutable` marks the member that is not part of that state.
    const std::string& at(std::size_t index) const {
        ++reads_;
        return entries_.at(index);
    }

    // Selected when the object is not const. It hands out a writable reference.
    std::string& at(std::size_t index) {
        ++handouts_;
        return entries_.at(index);
    }

    std::size_t size() const { return entries_.size(); }
    int reads() const { return reads_; }
    int handouts() const { return handouts_; }

private:
    std::vector<std::string> entries_;
    mutable int reads_ = 0;
    int handouts_ = 0;
};

int main() {
    Catalogue catalogue{{"alpha", "beta", "gamma"}};
    const Catalogue& frozen = catalogue;

    std::println("size: {}", frozen.size());
    std::println("read through the const reference: {}", frozen.at(0));

    // The object is not const, so this name selects the non-const overload.
    catalogue.at(1) = "BETA";
    std::println("after writing through the mutable name: {}", frozen.at(1));

    std::println("const overload calls:   {}", frozen.reads());
    std::println("mutable overload calls: {}", frozen.handouts());
}
