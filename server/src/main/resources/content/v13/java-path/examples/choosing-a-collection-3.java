import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class KeysThatMove {

    static final class Tag {
        String name;

        Tag(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Tag t && Objects.equals(name, t.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }

        @Override
        public String toString() {
            return "Tag[" + name + "]";
        }
    }

    public static void main(String[] args) {
        Tag tag = new Tag("draft");
        Set<Tag> tags = new HashSet<>();
        tags.add(tag);
        System.out.println("contains before : " + tags.contains(tag));

        tag.name = "published";
        System.out.println("contains after  : " + tags.contains(tag));
        System.out.println("size            : " + tags.size());
        System.out.println("iteration finds : " + tags.iterator().next());
        System.out.println("remove works    : " + tags.remove(tag));
        System.out.println("size now        : " + tags.size());

        List<String> immutable = List.of("a", "b");
        try {
            immutable.contains(null);
        } catch (NullPointerException e) {
            System.out.println("List.of + null  : NullPointerException");
        }
        System.out.println("ArrayList + null: " + new ArrayList<>(immutable).contains(null));
    }
}
