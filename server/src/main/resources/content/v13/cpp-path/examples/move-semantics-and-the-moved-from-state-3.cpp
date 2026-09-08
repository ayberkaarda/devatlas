// What is left behind: specified for some types, and only "valid" for others.
#include <memory>
#include <print>
#include <string>
#include <utility>
#include <vector>

// When you write the move constructor, you choose the moved-from state and you
// are the one who has to document it. This one empties the account.
struct Account {
    std::string owner;
    int balance = 0;

    Account(std::string name, int amount) : owner(std::move(name)), balance(amount) {}

    Account(Account&& other) noexcept
        : owner(std::move(other.owner)), balance(std::exchange(other.balance, 0)) {}

    Account& operator=(Account&& other) noexcept {
        if (this != &other) {
            owner = std::move(other.owner);
            balance = std::exchange(other.balance, 0);
        }
        return *this;
    }

    Account(const Account&) = delete;
    Account& operator=(const Account&) = delete;
    ~Account() = default;
};

int main() {
    // Specified by the standard: a moved-from unique_ptr is empty.
    std::unique_ptr<int> owner = std::make_unique<int>(5);
    std::unique_ptr<int> taken = std::move(owner);
    std::println("moved-from unique_ptr is empty: {}", owner == nullptr);
    std::println("the value travelled: {}", *taken);

    // Specified by this class, because this class wrote the constructor.
    Account from{"ada", 100};
    Account to = std::move(from);
    std::println("moved-to balance: {}", to.balance);
    std::println("moved-from balance, as documented: {}", from.balance);

    // Not specified for a standard string: the object is valid, so it may be
    // assigned to, and after that it is an ordinary string again. Its contents
    // between the move and the assignment are nobody's business.
    std::string text = "some characters";
    std::string sink = std::move(text);
    std::println("moved-to string size: {}", sink.size());
    text = "assigned after the move";
    std::println("usable again after assignment: size={}", text.size());

    // The same rule for a vector, with clear() as the other way back to a known
    // state: it is defined on any valid vector.
    std::vector<int> numbers{1, 2, 3};
    std::vector<int> adopted = std::move(numbers);
    std::println("moved-to vector size: {}", adopted.size());
    numbers.clear();
    numbers.push_back(9);
    std::println("reusable after clear: size={} front={}", numbers.size(), numbers.front());
}
