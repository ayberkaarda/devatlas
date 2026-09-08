// Which containers move their elements, and which promise not to.
#include <cstddef>
#include <iterator>
#include <list>
#include <map>
#include <print>
#include <string>
#include <vector>

int main() {
    std::vector<int> numbers;
    numbers.reserve(8);
    numbers.push_back(1);
    numbers.push_back(2);
    numbers.push_back(3);

    int* firstElement = &numbers.front();
    numbers.push_back(4);
    std::println("within capacity, nothing moved: {}", firstElement == &numbers.front());
    std::println("size={} capacity is at least size: {}", numbers.size(),
                 numbers.capacity() >= numbers.size());

    // Growing past the capacity relocates every element, so every pointer,
    // reference and iterator into the vector has to be obtained again.
    const std::size_t capacityBefore = numbers.capacity();
    numbers.reserve(capacityBefore + 1);
    firstElement = &numbers.front();
    std::println("capacity grew: {}", numbers.capacity() > capacityBefore);
    std::println("values survived: front={} back={} size={}", *firstElement, numbers.back(),
                 numbers.size());

    // A list stores each element in its own node, so insertion moves nothing.
    std::list<std::string> entries{"first", "second"};
    std::string& secondEntry = *std::next(entries.begin());
    entries.push_back("third");
    entries.push_front("zeroth");
    std::println("a list reference survives insertion: {}",
                 &secondEntry == &(*std::next(entries.begin(), 2)));
    std::println("the list now holds {} entries", entries.size());

    // A map is node-based too: references to elements stay valid across inserts.
    std::map<int, std::string> table{{1, "one"}, {3, "three"}};
    std::string& one = table.at(1);
    table.emplace(2, "two");
    table.emplace(4, "four");
    std::println("a map reference survives insertion: {}", &one == &table.at(1));
    std::println("the map now holds {} entries, and one is still {}", table.size(), one);
}
