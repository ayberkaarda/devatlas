import java.util.ArrayList;
import java.util.List;

final class ErasureAtRuntime {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static void main(String[] args) {
        List<String> strings = new ArrayList<>();
        List<Integer> integers = new ArrayList<>();

        System.out.println("same runtime class : " + (strings.getClass() == integers.getClass()));
        System.out.println("runtime class name : " + strings.getClass().getName());
        System.out.println("instanceof List<?> : " + (strings instanceof List<?>));

        // A raw type switches the compile-time checks off for this reference.
        List raw = strings;
        raw.add(Integer.valueOf(42));
        System.out.println("size after the add : " + strings.size());

        // The cast the compiler inserted at the read site is where it fails.
        try {
            String first = strings.get(0);
            System.out.println(first);
        } catch (ClassCastException e) {
            System.out.println("ClassCastException : " + e.getMessage());
        }

        // Iterating with Object never casts, so the polluted element comes back intact.
        for (Object element : strings) {
            System.out.println("as Object          : " + element.getClass().getName() + " " + element);
        }
    }
}
