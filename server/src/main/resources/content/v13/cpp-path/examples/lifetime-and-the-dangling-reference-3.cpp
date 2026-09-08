// Borrowing versus owning, and the window in which a borrow is valid.
#include <cstddef>
#include <print>
#include <string>

// Valid only while the referent is alive. Nothing in the type says how long that is,
// which is why a borrowing member is a contract the caller has to honour.
struct BorrowingLabel {
    const std::string& text;

    std::size_t size() const { return text.size(); }
};

// Valid on its own terms. It costs a copy and buys an independent lifetime.
struct OwningLabel {
    std::string text;

    std::size_t size() const { return text.size(); }
};

int main() {
    OwningLabel owning{""};

    {
        std::string source = "a source string";
        const BorrowingLabel borrowing{source};

        std::println("borrow sees {} characters", borrowing.size());
        owning = OwningLabel{source};
        std::println("owned copy has {} characters", owning.size());

        // The referent is the same object even after it grows, so the borrow
        // follows the change and the copy does not.
        source += " with more added later";
        std::println("after growth: borrow {}, owned copy {}", borrowing.size(), owning.size());
        std::println("the borrow names the source: {}", &borrowing.text == &source);
    }

    // `source` is gone. Only the owning label may still be read.
    std::println("owning outlived the block: {}", owning.text);
    std::println("owned size: {}", owning.size());
}
