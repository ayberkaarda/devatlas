// Order the standard promises, and order it declines to promise.
#include <algorithm>
#include <map>
#include <print>
#include <string>
#include <unordered_map>
#include <vector>

struct Record {
    std::string name;
    int group;
};

int main() {
    const std::vector<std::pair<std::string, int>> source{
        {"delta", 40}, {"alpha", 10}, {"charlie", 30}, {"bravo", 20}};

    // A map is ordered by key, so printing it is reproducible everywhere.
    std::map<std::string, int> ordered(source.begin(), source.end());
    std::string inKeyOrder;
    for (const auto& [key, value] : ordered) {
        if (!inKeyOrder.empty()) {
            inKeyOrder += ' ';
        }
        inKeyOrder += key;
    }
    std::println("map, in key order: {}", inKeyOrder);

    // An unordered_map's iteration order is unspecified, so nothing that depends
    // on it may be recorded. What can be recorded is what the container promises.
    std::unordered_map<std::string, int> fast(source.begin(), source.end());
    int total = 0;
    for (const auto& [key, value] : fast) {
        total += value;
    }
    bool everyKeyFound = true;
    for (const auto& [key, value] : source) {
        everyKeyFound = everyKeyFound && fast.contains(key) && fast.at(key) == value;
    }
    std::println("unordered_map size={} total={} every key found={}", fast.size(), total,
                 everyKeyFound);

    // std::sort may reorder elements that compare equal; std::stable_sort may not.
    std::vector<Record> records{{"ada", 1}, {"alan", 2}, {"grace", 1}, {"edsger", 2}};
    std::stable_sort(records.begin(), records.end(),
                     [](const Record& left, const Record& right) { return left.group < right.group; });

    std::string stableOrder;
    for (const Record& record : records) {
        if (!stableOrder.empty()) {
            stableOrder += ' ';
        }
        stableOrder += record.name;
    }
    std::println("stable_sort by group: {}", stableOrder);
    std::println("equal keys kept their original relative order: {}",
                 stableOrder == "ada grace alan edsger");
}
