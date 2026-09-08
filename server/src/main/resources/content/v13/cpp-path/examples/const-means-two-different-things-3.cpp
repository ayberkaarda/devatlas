// `const` describes the access path, not the object, and it is one level deep.
#include <print>
#include <string>

class Gauge {
public:
    explicit Gauge(int* reading) : reading_(reading) {}

    // A const member function: it may not change `reading_`, which is the pointer.
    // Nothing stops it changing what `reading_` points at, because that object is
    // not part of this object.
    void bump() const { ++*reading_; }

    int read() const { return *reading_; }

private:
    int* reading_;
};

int main() {
    int counter = 0;
    int& writableName = counter;
    const int& readOnlyName = counter;

    writableName = 7;
    std::println("the read-only name sees the write: {}", readOnlyName);
    std::println("both names denote one object: {}", &writableName == &readOnlyName);

    // The object was never const. Only one path to it is.
    writableName = 8;
    std::println("and again: {}", readOnlyName);

    int reading = 100;
    const Gauge gauge{&reading};
    gauge.bump();
    gauge.bump();
    std::println("a const object changed the world: reading={}", gauge.read());
    std::println("the pointer member itself never moved: {}", gauge.read() == reading);
}
