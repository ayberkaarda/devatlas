// Arithmetic that stays defined: check before adding, widen, or use unsigned.
#include <cstdint>
#include <limits>
#include <print>

// The check has to be written as a question about the operands, because asking it
// after the fact would mean performing the overflow first.
bool additionWouldOverflow(std::int32_t left, std::int32_t right) {
    if (right > 0 && left > std::numeric_limits<std::int32_t>::max() - right) {
        return true;
    }
    if (right < 0 && left < std::numeric_limits<std::int32_t>::min() - right) {
        return true;
    }
    return false;
}

int main() {
    constexpr std::int32_t highest = std::numeric_limits<std::int32_t>::max();
    constexpr std::int32_t lowest = std::numeric_limits<std::int32_t>::min();

    std::println("int32 range: {} to {}", lowest, highest);
    std::println("highest + 1 would overflow: {}", additionWouldOverflow(highest, 1));
    std::println("lowest - 1 would overflow:  {}", additionWouldOverflow(lowest, -1));
    std::println("2 + 2 would overflow:       {}", additionWouldOverflow(2, 2));

    // Widening first is the other defined answer: the addition happens in a type
    // that cannot overflow for these operands.
    const std::int64_t widened = static_cast<std::int64_t>(highest) + 1;
    std::println("widened before adding:      {}", widened);

    // Unsigned arithmetic is specified to wrap modulo two to the power of the width.
    constexpr std::uint32_t unsignedHighest = std::numeric_limits<std::uint32_t>::max();
    std::println("unsigned highest + 1:       {}", unsignedHighest + 1U);
    std::println("unsigned wrapping is specified: {}", (unsignedHighest + 1U) == 0U);

    // A shift count must be less than the width of the promoted left operand.
    constexpr int width = std::numeric_limits<std::uint32_t>::digits;
    std::println("uint32 has {} value bits", width);
    std::println("1u << {} = {}", width - 1, 1U << (width - 1));
}
