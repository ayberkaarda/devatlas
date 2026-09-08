import java.util.Locale;

final class SealedBasics {

    sealed interface Shape permits Circle, Square, Rectangle { }

    record Circle(double radius) implements Shape { }

    record Square(double side) implements Shape { }

    record Rectangle(double width, double height) implements Shape { }

    // No default label. The compiler knows these three are all there are.
    static double area(Shape shape) {
        return switch (shape) {
            case Circle c -> Math.PI * c.radius() * c.radius();
            case Square s -> s.side() * s.side();
            case Rectangle r -> r.width() * r.height();
        };
    }

    public static void main(String[] args) {
        Shape[] shapes = { new Circle(1), new Square(2), new Rectangle(2, 3) };
        for (Shape shape : shapes) {
            System.out.println(String.format(Locale.ROOT, "%-32s area %.4f", shape, area(shape)));
        }

        System.out.println("Shape.isSealed : " + Shape.class.isSealed());
        for (Class<?> permitted : Shape.class.getPermittedSubclasses()) {
            System.out.println("permits        : " + permitted.getSimpleName());
        }
        System.out.println("Circle.isSealed: " + Circle.class.isSealed());
    }
}
