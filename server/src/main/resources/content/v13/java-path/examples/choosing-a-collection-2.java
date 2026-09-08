import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SequencedCollection;
import java.util.SequencedMap;

final class SequencedViews {

    public static void main(String[] args) {
        List<String> items = new ArrayList<>(List.of("first", "second", "third"));

        System.out.println("getFirst        : " + items.getFirst());
        System.out.println("getLast         : " + items.getLast());
        System.out.println("reversed        : " + items.reversed());

        // reversed() is a view, not a copy: the original shows the change.
        List<String> backwards = items.reversed();
        backwards.set(0, "third-renamed");
        System.out.println("original after  : " + items);

        SequencedCollection<String> asSequenced = items;
        asSequenced.addFirst("zeroth");
        System.out.println("after addFirst  : " + items);

        SequencedMap<String, Integer> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);
        System.out.println("firstEntry      : " + map.firstEntry());
        System.out.println("lastEntry       : " + map.lastEntry());
        System.out.println("reversed keys   : " + map.reversed().keySet());
        System.out.println("pollFirstEntry  : " + map.pollFirstEntry() + " leaves " + map);

        LinkedHashSet<String> set = new LinkedHashSet<>(List.of("x", "y", "z"));
        System.out.println("set reversed    : " + set.reversed());
    }
}
