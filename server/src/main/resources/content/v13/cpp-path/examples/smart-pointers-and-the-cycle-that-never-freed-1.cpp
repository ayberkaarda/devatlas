// unique_ptr: one owner, transferable, and a moved-from state the standard specifies.
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

std::unique_ptr<Resource> makeResource(std::string name) {
    return std::make_unique<Resource>(std::move(name));
}

int main() {
    std::println("alive at the start: {}", Resource::alive);

    {
        std::unique_ptr<Resource> owner = makeResource("primary");
        std::println("after make_unique:  alive={} owner holds one={}", Resource::alive,
                     owner != nullptr);

        std::unique_ptr<Resource> adopted = std::move(owner);
        std::println("after the move:     source is null={} target holds one={}",
                     owner == nullptr, adopted != nullptr);
        std::println("still one object:   alive={} name={}", Resource::alive, adopted->name);
    }

    std::println("after the block:    alive={}", Resource::alive);

    std::unique_ptr<Resource> held = makeResource("second");
    Resource* handedBack = held.release();
    std::println("after release():    pointer is null={} alive={}", held == nullptr,
                 Resource::alive);

    held.reset(handedBack);
    std::println("after reset(p):     pointer holds one={} alive={}", held != nullptr,
                 Resource::alive);

    held.reset();
    std::println("after reset():      alive={}", Resource::alive);
}
