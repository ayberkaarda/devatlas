// Java 21. Entity identity written the way a row's identity works: equality on the
// primary key. The object is in a collection before the key exists, and vanishes.
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

class EntityIdentityDemo {

  // Equality delegated to the identifier, which is the usual first instinct: two
  // rows are the same row when their primary keys match.
  static final class TrackByIdentifier {
    private UUID id;
    private final String slug;

    TrackByIdentifier(String slug) {
      this.slug = slug;
    }

    void assignIdentifier(UUID id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof TrackByIdentifier track && Objects.equals(id, track.id);
    }

    @Override
    public int hashCode() {
      return Objects.hash(id);
    }

    @Override
    public String toString() {
      return slug;
    }
  }

  // Equality on a value the object is born with and never changes. The identifier
  // may still be assigned later; the hash code does not move when it is.
  static final class TrackBySlug {
    private UUID id;
    private final String slug;

    TrackBySlug(String slug) {
      this.slug = Objects.requireNonNull(slug);
    }

    void assignIdentifier(UUID id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof TrackBySlug track && slug.equals(track.slug);
    }

    @Override
    public int hashCode() {
      return slug.hashCode();
    }

    @Override
    public String toString() {
      return slug;
    }
  }

  public static void main(String[] args) {
    UUID generated = UUID.fromString("019205a0-1000-7000-8000-000000000002");

    TrackByIdentifier byIdentifier = new TrackByIdentifier("spring-boot-path");
    Set<TrackByIdentifier> keyedById = new HashSet<>();
    keyedById.add(byIdentifier);
    System.out.println("before the key exists, the set contains it: " + keyedById.contains(byIdentifier));

    // The database, or a UUID generator, supplies the key at insert time.
    byIdentifier.assignIdentifier(generated);
    System.out.println("after the key is assigned, the set contains it: " + keyedById.contains(byIdentifier));
    System.out.println("the set still holds one element: " + keyedById.size());
    System.out.println("and iterating finds it: " + keyedById.iterator().next());

    TrackBySlug bySlug = new TrackBySlug("spring-boot-path");
    Set<TrackBySlug> keyedBySlug = new HashSet<>();
    keyedBySlug.add(bySlug);
    bySlug.assignIdentifier(generated);
    System.out.println("keyed on an immutable value, still found: " + keyedBySlug.contains(bySlug));
  }
}
