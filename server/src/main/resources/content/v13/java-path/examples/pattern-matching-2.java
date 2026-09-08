final class NullAndGuards {

    // A switch with pattern labels and no null label throws on null, as switch always has.
    static String withoutNullLabel(Object value) {
        return switch (value) {
            case String s -> "string " + s;
            default -> "other";
        };
    }

    // A null label is allowed, and may only be combined with default.
    static String withNullLabel(Object value) {
        return switch (value) {
            case null -> "nothing";
            case String s -> "string " + s;
            default -> "other";
        };
    }

    // Guards refine a pattern. Order matters: an unguarded pattern would dominate.
    static String classify(Object value) {
        return switch (value) {
            case Integer i when i < 0 -> "negative";
            case Integer i when i == 0 -> "zero";
            case Integer i -> "positive " + i;
            case String s when s.isEmpty() -> "empty string";
            case String s -> "string of " + s.length();
            default -> "unhandled " + value.getClass().getSimpleName();
        };
    }

    public static void main(String[] args) {
        try {
            withoutNullLabel(null);
        } catch (NullPointerException e) {
            System.out.println("no null label : NullPointerException");
        }
        System.out.println("with null label: " + withNullLabel(null));
        System.out.println("with null label: " + withNullLabel("x"));

        System.out.println(classify(-3));
        System.out.println(classify(0));
        System.out.println(classify(7));
        System.out.println(classify(""));
        System.out.println(classify("abcd"));
        System.out.println(classify(1.5));
    }
}
