import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class RecordsAsKeys {

    record SeatId(String coach, int number) { }

    // The same data as a plain class, with no equals or hashCode written.
    static final class PlainSeatId {
        final String coach;
        final int number;

        PlainSeatId(String coach, int number) {
            this.coach = Objects.requireNonNull(coach);
            this.number = number;
        }
    }

    public static void main(String[] args) {
        Map<SeatId, String> byRecord = new HashMap<>();
        byRecord.put(new SeatId("A", 12), "Ada");
        System.out.println("record lookup : " + byRecord.get(new SeatId("A", 12)));

        Map<PlainSeatId, String> byPlainClass = new HashMap<>();
        byPlainClass.put(new PlainSeatId("A", 12), "Ada");
        System.out.println("plain lookup  : " + byPlainClass.get(new PlainSeatId("A", 12)));

        Set<SeatId> seats = new HashSet<>();
        seats.add(new SeatId("A", 12));
        seats.add(new SeatId("A", 12));
        seats.add(new SeatId("B", 12));
        System.out.println("distinct seats: " + seats.size());
    }
}
