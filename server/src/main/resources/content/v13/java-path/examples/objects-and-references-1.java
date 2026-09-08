import java.util.ArrayList;
import java.util.List;

final class ReferenceMechanics {

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

    // Mutating the object the reference points at is visible to the caller.
    static void rename(Box box) {
        box.label = "renamed";
    }

    // Rebinding the parameter is not: the parameter holds a copy of the reference.
    static void replace(Box box) {
        box = new Box("replacement");
        box.label = "written to an object nobody else can see";
    }

    static void addTo(List<String> names) {
        names.add("added by callee");
    }

    public static void main(String[] args) {
        Box a = new Box("original");
        rename(a);
        System.out.println("after rename : " + a);

        replace(a);
        System.out.println("after replace: " + a);

        Box b = a;
        b.label = "changed through b";
        System.out.println("a == b       : " + (a == b));
        System.out.println("a reads      : " + a);

        List<String> names = new ArrayList<>(List.of("first"));
        addTo(names);
        System.out.println("caller's list: " + names);

        Box nothing = null;
        try {
            System.out.println(nothing.label);
        } catch (NullPointerException e) {
            System.out.println("NPE message  : " + e.getMessage());
        }
    }
}
