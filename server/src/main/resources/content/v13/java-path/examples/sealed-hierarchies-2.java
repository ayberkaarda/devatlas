import java.util.Arrays;

final class SealingChoices {

    // The permits clause may be omitted when every subtype is in this source file.
    sealed interface Event { }

    record Created(String id) implements Event { }

    record Deleted(String id) implements Event { }

    // Deliberately reopened: anyone may extend Custom, but nothing else may extend Event.
    non-sealed interface Custom extends Event { }

    record Renamed(String id, String name) implements Custom { }

    static String describe(Event event) {
        return switch (event) {
            case Created c -> "created " + c.id();
            case Deleted d -> "deleted " + d.id();
            case Custom c -> "custom " + c.getClass().getSimpleName();
        };
    }

    public static void main(String[] args) {
        System.out.println(describe(new Created("a")));
        System.out.println(describe(new Deleted("b")));
        System.out.println(describe(new Renamed("c", "invoice")));

        System.out.println("Event permits  : "
                + Arrays.stream(Event.class.getPermittedSubclasses())
                        .map(Class::getSimpleName)
                        .toList());
        System.out.println("Custom isSealed: " + Custom.class.isSealed());
        System.out.println("Renamed is an Event: " + (new Renamed("c", "invoice") instanceof Event));
    }
}
