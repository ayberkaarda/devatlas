import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class LoopAndStream {

    record Employee(String name, String department, int salary) { }

    static final List<Employee> STAFF = List.of(
            new Employee("Ada", "engineering", 120),
            new Employee("Grace", "engineering", 130),
            new Employee("Edsger", "research", 110),
            new Employee("Barbara", "research", 140));

    static List<String> loopVersion() {
        List<String> names = new ArrayList<>();
        for (Employee e : STAFF) {
            if (e.salary() >= 120) {
                names.add(e.name());
            }
        }
        return names;
    }

    static List<String> streamVersion() {
        return STAFF.stream()
                .filter(e -> e.salary() >= 120)
                .map(Employee::name)
                .toList();
    }

    // Nothing runs until the terminal operation asks for an element.
    static Optional<String> firstHighEarner() {
        return STAFF.stream()
                .filter(e -> {
                    System.out.println("  testing " + e.name());
                    return e.salary() >= 130;
                })
                .map(Employee::name)
                .findFirst();
    }

    public static void main(String[] args) {
        System.out.println("loop   : " + loopVersion());
        System.out.println("stream : " + streamVersion());
        System.out.println("equal  : " + loopVersion().equals(streamVersion()));

        System.out.println("lazily, element at a time:");
        System.out.println("result : " + firstHighEarner().orElse("none"));

        // A pipeline that is never terminated does no work at all.
        STAFF.stream().map(e -> {
            System.out.println("  never printed");
            return e;
        });
        System.out.println("no terminal operation, no output above");
    }
}
