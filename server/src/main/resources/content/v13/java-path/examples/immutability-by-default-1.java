import java.util.ArrayList;
import java.util.List;

final class DefensiveCopies {

    // The record's field is final, but the list it points at is not.
    record Leaky(String name, List<String> tags) { }

    record Guarded(String name, List<String> tags) {
        Guarded {
            tags = List.copyOf(tags);
        }
    }

    public static void main(String[] args) {
        List<String> caller = new ArrayList<>(List.of("java", "jvm"));
        Leaky leaky = new Leaky("guide", caller);
        caller.add("added after construction");
        System.out.println("leaky sees      : " + leaky.tags());

        leaky.tags().clear();
        System.out.println("caller sees     : " + caller);

        List<String> other = new ArrayList<>(List.of("java", "jvm"));
        Guarded guarded = new Guarded("guide", other);
        other.add("added after construction");
        System.out.println("guarded sees    : " + guarded.tags());

        try {
            guarded.tags().add("no");
        } catch (UnsupportedOperationException e) {
            System.out.println("mutating result : UnsupportedOperationException");
        }
    }
}
