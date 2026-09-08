// Spring Data JPA 4.1 (Spring Boot 4.1). A derived query method name is parsed, not
// pattern-matched. This prints the tree Spring Data builds from the name.
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

class DerivedQueryParsingDemo {

  static class LessonRow {
    private UUID id;
    private UUID moduleId;
    private String slug;
    private Instant deletedAt;
    private int displayOrder;

    public UUID getId() {
      return id;
    }

    public UUID getModuleId() {
      return moduleId;
    }

    public String getSlug() {
      return slug;
    }

    public Instant getDeletedAt() {
      return deletedAt;
    }

    public int getDisplayOrder() {
      return displayOrder;
    }
  }

  static void explain(String methodName) {
    PartTree tree = new PartTree(methodName, LessonRow.class);
    System.out.println(methodName);
    for (Part part : tree.getParts()) {
      System.out.println(
          "  property=" + part.getProperty() + " type=" + part.getType() + " args=" + part.getNumberOfArguments());
    }
    System.out.println("  sort=" + tree.getSort());
    System.out.println("  limiting=" + tree.isLimiting() + " maxResults=" + tree.getMaxResults());
  }

  public static void main(String[] args) {
    explain("findBySlugAndDeletedAtIsNull");
    explain("findByModuleIdAndDeletedAtIsNullOrderByDisplayOrderAsc");
    explain("findTop3BySlugContainingIgnoreCaseOrderBySlugAsc");
    explain("existsBySlugAndDeletedAtIsNullAndIdNot");
  }
}
