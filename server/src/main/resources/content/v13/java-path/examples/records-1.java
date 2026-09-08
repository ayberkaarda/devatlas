import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;

final class RecordBasics {

    record Point(int x, int y) { }

    public static void main(String[] args) {
        Point a = new Point(3, 4);
        Point b = new Point(3, 4);

        System.out.println("toString        : " + a);
        System.out.println("accessors       : " + a.x() + ", " + a.y());
        System.out.println("a.equals(b)     : " + a.equals(b));
        System.out.println("hashCodes agree : " + (a.hashCode() == b.hashCode()));
        System.out.println("a == b          : " + (a == b));

        System.out.println("superclass      : " + Point.class.getSuperclass().getName());
        System.out.println("isRecord        : " + Point.class.isRecord());
        System.out.println("implicitly final: " + Modifier.isFinal(Point.class.getModifiers()));

        for (RecordComponent c : Point.class.getRecordComponents()) {
            System.out.println("component       : " + c.getType().getSimpleName() + " " + c.getName());
        }
    }
}
