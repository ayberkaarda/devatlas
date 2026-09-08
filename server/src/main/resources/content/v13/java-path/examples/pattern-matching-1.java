final class PatternBasics {

    sealed interface Shape permits Circle, Rectangle { }

    record Point(int x, int y) { }

    record Circle(Point centre, int radius) implements Shape { }

    record Rectangle(Point topLeft, Point bottomRight) implements Shape { }

    // The old shape: test, cast, then use.
    static String describeOldStyle(Object value) {
        if (value instanceof String) {
            String s = (String) value;
            return "string of length " + s.length();
        }
        return "something else";
    }

    // The pattern binds the variable only where the test succeeded.
    static String describe(Object value) {
        if (value instanceof String s && !s.isBlank()) {
            return "string of length " + s.length();
        }
        return "something else";
    }

    // A record pattern destructures in the label itself.
    static String summarise(Shape shape) {
        return switch (shape) {
            case Circle(Point(int x, int y), int r) -> "circle at " + x + "," + y + " radius " + r;
            case Rectangle(Point(int x1, int y1), Point(int x2, int y2)) ->
                    "rectangle " + (x2 - x1) + " by " + (y2 - y1);
        };
    }

    public static void main(String[] args) {
        System.out.println(describeOldStyle("hello"));
        System.out.println(describe("hello"));
        System.out.println(describe("   "));
        System.out.println(describe(42));

        System.out.println(summarise(new Circle(new Point(1, 2), 5)));
        System.out.println(summarise(new Rectangle(new Point(0, 0), new Point(4, 3))));
    }
}
