// shared_ptr: how many owners there are, and what a weak_ptr does not add.
#include <memory>
#include <print>
#include <string>
#include <utility>

struct Resource {
    static inline int alive = 0;

    std::string name;

    explicit Resource(std::string label) : name(std::move(label)) { ++alive; }
    ~Resource() { --alive; }

    Resource(const Resource&) = delete;
    Resource& operator=(const Resource&) = delete;
};

int main() {
    std::shared_ptr<Resource> first = std::make_shared<Resource>("shared");
    std::println("one owner:            use_count={} alive={}", first.use_count(),
                 Resource::alive);

    std::weak_ptr<Resource> observer = first;
    std::println("a weak_ptr was added: use_count={} expired={}", first.use_count(),
                 observer.expired());

    {
        std::shared_ptr<Resource> second = first;
        std::println("two owners:           use_count={} alive={}", first.use_count(),
                     Resource::alive);
        std::println("both name one object: {}", first.get() == second.get());
    }

    std::println("back to one owner:    use_count={} alive={}", first.use_count(),
                 Resource::alive);

    {
        std::shared_ptr<Resource> locked = observer.lock();
        std::println("lock() gave an owner: use_count={} name={}", first.use_count(),
                     locked->name);
    }

    first.reset();
    std::println("last owner released:  alive={} expired={}", Resource::alive,
                 observer.expired());
    std::println("lock() now gives null: {}", observer.lock() == nullptr);
}
