import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class CollectingResults {

    record Employee(String name, String department) { }

    static final List<Employee> STAFF = List.of(
            new Employee("Ada", "engineering"),
            new Employee("Grace", "engineering"),
            new Employee("Edsger", "research"));

    public static void main(String[] args) {
        List<String> fromToList = STAFF.stream().map(Employee::name).toList();
        try {
            fromToList.add("Alan");
        } catch (UnsupportedOperationException e) {
            System.out.println("Stream.toList     : UnsupportedOperationException on add");
        }

        List<String> fromCollector = STAFF.stream().map(Employee::name).collect(Collectors.toList());
        fromCollector.add("Alan");
        System.out.println("Collectors.toList : " + fromCollector);

        Map<String, List<String>> byDepartment = STAFF.stream()
                .collect(Collectors.groupingBy(Employee::department,
                        Collectors.mapping(Employee::name, Collectors.toList())));
        System.out.println("groupingBy        : " + byDepartment);

        try {
            STAFF.stream().collect(Collectors.toMap(Employee::department, Employee::name));
        } catch (IllegalStateException e) {
            System.out.println("toMap duplicate   : " + e.getMessage());
        }

        Map<String, String> merged = STAFF.stream()
                .collect(Collectors.toMap(Employee::department, Employee::name, (a, b) -> a + " and " + b));
        System.out.println("toMap with merge  : " + merged);

        // Stream.toList accepts nulls; the unmodifiable collector does not.
        System.out.println("toList with null  : " + Stream.of("a", null).toList());
        try {
            Stream.of("a", (String) null).collect(Collectors.toUnmodifiableList());
        } catch (NullPointerException e) {
            System.out.println("toUnmodifiableList: NullPointerException");
        }
    }
}
