// Where `const` binds: to the pointer, to what it points at, or to both.
#include <print>

int main() {
    int first = 1;
    int second = 2;

    // `const` applies to what is on its left; with nothing on its left it applies
    // to what is on its right. Both spellings below mean "pointer to const int".
    const int* pointeeIsConst = &first;
    int const* alsoPointeeIsConst = &first;

    // Here `const` is to the right of the `*`, so the pointer itself is const.
    int* const pointerIsConst = &first;

    // And both.
    const int* const bothAreConst = &first;

    // Legal: re-point a pointer whose pointee is const.
    pointeeIsConst = &second;
    alsoPointeeIsConst = &second;
    std::println("re-pointed to second, reading {}", *pointeeIsConst);
    std::println("both spellings agree: {}", pointeeIsConst == alsoPointeeIsConst);

    // Legal: write through a pointer that is itself const.
    *pointerIsConst = 41;
    std::println("wrote through a const pointer, first is now {}", first);

    // Reading through the fully const pointer sees the same object.
    std::println("bothAreConst reads {}", *bothAreConst);
    std::println("bothAreConst names first: {}", bothAreConst == &first);

    // A reference is const in one way only: there is no "const reference to
    // non-const" versus "reference that cannot be re-seated", because no
    // reference can ever be re-seated.
    const int& frozen = first;
    first = 42;
    std::println("the const reference sees the write: {}", frozen);
}
