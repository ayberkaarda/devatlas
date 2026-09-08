import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

final class WhenALoopIsClearer {

    public static void main(String[] args) {
        Stream<String> once = Stream.of("a", "b", "c");
        System.out.println("first use  : " + once.count());
        try {
            once.count();
        } catch (IllegalStateException e) {
            System.out.println("second use : " + e.getMessage());
        }

        // A pipeline that mutates state outside itself is a loop wearing a costume.
        List<String> collected = new ArrayList<>();
        Stream.of("a", "b", "c").forEach(collected::add);
        System.out.println("side effect: " + collected);
        System.out.println("plainly    : " + Stream.of("a", "b", "c").toList());

        // Early exit reads better as a loop when the condition is not a predicate.
        int[] readings = { 3, 9, 4, 12, 7 };
        int firstOverTen = -1;
        for (int reading : readings) {
            if (reading > 10) {
                firstOverTen = reading;
                break;
            }
        }
        System.out.println("loop found : " + firstOverTen);
        System.out.println("stream found: " + IntStream.of(readings).filter(r -> r > 10).findFirst().orElse(-1));

        // Sum with an index is where a stream stops helping.
        int weighted = 0;
        for (int i = 0; i < readings.length; i++) {
            weighted += readings[i] * i;
        }
        System.out.println("weighted   : " + weighted);
        System.out.println("same, boxed: " + IntStream.range(0, readings.length).map(i -> readings[i] * i).sum());
    }
}
