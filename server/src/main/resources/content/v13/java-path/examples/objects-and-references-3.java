import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ShallowCopies {

    static final class Box {
        String label;

        Box(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return "Box[" + label + "]";
        }
    }

    public static void main(String[] args) {
        Box[] original = { new Box("one"), new Box("two") };
        Box[] copy = Arrays.copyOf(original, original.length);

        System.out.println("arrays are different : " + (original != copy));
        System.out.println("element 0 is shared  : " + (original[0] == copy[0]));

        copy[0].label = "mutated through copy";
        System.out.println("original[0]          : " + original[0]);

        copy[1] = new Box("only in the copy");
        System.out.println("original[1]          : " + original[1]);

        List<Box> list = new ArrayList<>(Arrays.asList(original));
        List<Box> listCopy = new ArrayList<>(list);
        listCopy.get(0).label = "mutated through list copy";
        System.out.println("list.get(0)          : " + list.get(0));
        System.out.println("lists are different  : " + (list != listCopy));
    }
}
