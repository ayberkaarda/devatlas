// Removing elements without stepping off the end of what you are modifying.
#include <map>
#include <print>
#include <string>
#include <vector>

std::string joinInts(const std::vector<int>& values) {
    std::string out;
    for (const int value : values) {
        if (!out.empty()) {
            out += ' ';
        }
        out += std::to_string(value);
    }
    return out;
}

std::string joinStrings(const std::vector<std::string>& values) {
    std::string out;
    for (const std::string& value : values) {
        if (!out.empty()) {
            out += ' ';
        }
        out += value;
    }
    return out;
}

int main() {
    // std::erase_if does the whole job and reports how many went.
    std::vector<int> numbers{1, 2, 3, 4, 5, 6, 7, 8};
    const std::size_t removed = std::erase_if(numbers, [](int value) { return value % 2 == 0; });
    std::println("erase_if removed {} of 8, leaving: {}", removed, joinInts(numbers));

    // Erasing inside a loop: erase returns the iterator to the next element, and
    // that returned iterator is the only one you may keep using.
    std::vector<std::string> names{"ada", "alan", "grace", "edsger", "ken"};
    for (auto it = names.begin(); it != names.end();) {
        if (it->size() > 4) {
            it = names.erase(it);
        } else {
            ++it;
        }
    }
    std::println("names of four characters or fewer: {}", joinStrings(names));

    // The same shape for a node-based container, where only the erased element's
    // iterator is invalidated.
    std::map<std::string, int> scores{{"ada", 10}, {"alan", 3}, {"grace", 7}, {"ken", 1}};
    for (auto it = scores.begin(); it != scores.end();) {
        if (it->second < 5) {
            it = scores.erase(it);
        } else {
            ++it;
        }
    }

    std::string kept;
    for (const auto& [name, score] : scores) {  // a map iterates in key order
        if (!kept.empty()) {
            kept += ", ";
        }
        kept += std::format("{}={}", name, score);
    }
    std::println("scores of five or more, in key order: {}", kept);
    std::println("{} entries remain", scores.size());
}
