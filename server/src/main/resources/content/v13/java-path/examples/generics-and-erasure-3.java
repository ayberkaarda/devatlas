import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

final class BridgeMethodsAndBounds {

    static final class Version implements Comparable<Version> {
        final int number;

        Version(int number) {
            this.number = number;
        }

        @Override
        public int compareTo(Version other) {
            return Integer.compare(number, other.number);
        }

        @Override
        public String toString() {
            return "v" + number;
        }
    }

    // A type variable erases to the erasure of its leftmost bound.
    static <T extends Comparable<T>> T largest(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static void main(String[] args) throws Exception {
        Method[] declared = Version.class.getDeclaredMethods();
        Arrays.sort(declared, Comparator.comparing(Method::toString));
        for (Method m : declared) {
            if (!m.getName().equals("compareTo")) {
                continue;
            }
            System.out.println("compareTo(" + m.getParameterTypes()[0].getSimpleName() + ")"
                    + " bridge=" + m.isBridge() + " synthetic=" + m.isSynthetic());
        }

        Version[] versions = { new Version(3), new Version(1), new Version(2) };
        Arrays.sort(versions);
        System.out.println("sorted             : " + Arrays.toString(versions));

        Method largest = BridgeMethodsAndBounds.class.getDeclaredMethod("largest",
                Comparable.class, Comparable.class);
        System.out.println("erased parameter   : " + largest.getParameterTypes()[0].getName());
        System.out.println("declared parameter : " + largest.getGenericParameterTypes()[0]);
        System.out.println("largest of v1, v2  : " + largest(new Version(1), new Version(2)));
    }
}
