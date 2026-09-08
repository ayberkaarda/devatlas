import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class CopyCosts {

    public static void main(String[] args) {
        List<String> unmodifiable = List.of("a", "b", "c");
        System.out.println("copyOf of List.of shares : " + (unmodifiable == List.copyOf(unmodifiable)));

        List<String> mutable = new ArrayList<>(unmodifiable);
        System.out.println("copyOf of ArrayList copies: " + (mutable != List.copyOf(mutable)));

        List<String> fixedSize = Arrays.asList("a", "b", "c");
        fixedSize.set(0, "z");
        System.out.println("Arrays.asList allows set  : " + fixedSize);
        try {
            fixedSize.add("d");
        } catch (UnsupportedOperationException e) {
            System.out.println("Arrays.asList refuses add : UnsupportedOperationException");
        }

        String[] backing = { "a", "b" };
        List<String> view = Arrays.asList(backing);
        backing[0] = "changed in the array";
        System.out.println("the view follows the array: " + view);

        try {
            List.of("a", null);
        } catch (NullPointerException e) {
            System.out.println("List.of refuses null      : NullPointerException");
        }
    }
}
