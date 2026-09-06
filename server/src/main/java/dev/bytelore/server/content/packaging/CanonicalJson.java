package dev.bytelore.server.content.packaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.json.JsonMapper;

/**
 * The exact byte-level procedure of §3.2 of the content sync protocol: UTF-8, no BOM, object keys
 * sorted by Unicode code point ascending, no insignificant whitespace, minimal string escaping
 * (non-ASCII emitted literally, never as a backslash-u escape), integers only, arrays preserving
 * their given order, and {@code null} never emitted -- an absent value is an absent key.
 *
 * <p>Deliberately a standalone utility with its own {@link JsonMapper} instance, independent of the
 * application's shared, Spring-configured mapper: a package carries no {@code Instant} field and
 * needs none of that mapper's customizations, and keeping this self-contained is what makes it
 * testable with no Spring context at all (see {@code ContentPackagerDeterminismTest}).
 *
 * <p>Public because the manifest is hashed by the same rules: §4.2 defines a track manifest's
 * {@code ETag} as the SHA-256 of its canonical bytes, and §5's ordering rules only produce a
 * reproducible manifest if the same serializer writes it. The manifest endpoints therefore build a
 * tree and hand it to this class rather than serializing through the application's own mapper,
 * whose key order is declaration order.
 *
 * <p>Every {@link Map} encountered -- top-level or nested, however it was built -- is copied into a
 * {@link TreeMap} before serialization, so the caller never has to pre-sort anything and two
 * packages assembled by inserting the same fields in a different order produce byte-identical
 * output (§3.5: "object built with keys in two different insertion orders" -> identical digest).
 * Natural {@link String} ordering is Unicode code point ordering for the ASCII field names this
 * protocol uses, so {@link TreeMap}'s default ordering is exactly the rule §3.2 asks for.
 */
public final class CanonicalJson {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private CanonicalJson() {}

  /**
   * Serializes {@code value} (a {@link Map}, ordinarily) to canonical bytes: keys sorted
   * recursively, {@code null} values dropped recursively, list order preserved.
   */
  public static byte[] bytes(Object value) {
    return MAPPER.writeValueAsBytes(canonicalize(value));
  }

  private static Object canonicalize(Object value) {
    if (value instanceof Map<?, ?> map) {
      TreeMap<String, Object> sorted = new TreeMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object entryValue = entry.getValue();
        if (entryValue == null) {
          // Rule 7: null is never emitted. An absent value is an absent key.
          continue;
        }
        sorted.put(String.valueOf(entry.getKey()), canonicalize(entryValue));
      }
      return sorted;
    }
    if (value instanceof List<?> list) {
      List<Object> result = new ArrayList<>(list.size());
      for (Object element : list) {
        result.add(canonicalize(element));
      }
      return result;
    }
    // Strings, integers and booleans pass through as-is. The protocol defines no floating-point
    // field (rule 5), so nothing here ever needs to guard against one.
    return value;
  }
}
