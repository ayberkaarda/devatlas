import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

final class MapOrdering {

    static final String[] KEYS = { "delta", "alpha", "charlie", "bravo", "echo" };

    static void fill(Map<String, Integer> map) {
        for (int i = 0; i < KEYS.length; i++) {
            map.put(KEYS[i], i);
        }
    }

    public static void main(String[] args) {
        Map<String, Integer> hash = new HashMap<>();
        Map<String, Integer> linked = new LinkedHashMap<>();
        Map<String, Integer> tree = new TreeMap<>();
        fill(hash);
        fill(linked);
        fill(tree);

        // HashMap makes no guarantee about order; this run's order is one legal answer.
        System.out.println("insertion order : " + java.util.Arrays.toString(KEYS));
        System.out.println("HashMap         : " + hash.keySet());
        System.out.println("LinkedHashMap   : " + linked.keySet());
        System.out.println("TreeMap         : " + tree.keySet());

        System.out.println("HashMap == same : " + hash.equals(linked));
        System.out.println("TreeMap first   : " + ((TreeMap<String, Integer>) tree).firstKey());

        Map<String, Integer> immutable = Map.of("a", 1);
        try {
            immutable.put("b", 2);
        } catch (UnsupportedOperationException e) {
            System.out.println("Map.of is fixed : UnsupportedOperationException");
        }
    }
}
