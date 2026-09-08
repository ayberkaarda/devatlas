// Spring Boot 4.1 (Spring Framework 7.0). A container, two ordinary objects, and
// nothing in either of them that knows a container exists.
import java.util.List;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class BeanContainerDemo {

  record LessonRow(String slug) {}

  // A collaborator. No annotation, no lookup, no framework type in any signature.
  static class LessonRepository {
    private final List<LessonRow> rows =
        List.of(new LessonRow("a-bean-and-who-makes-it"), new LessonRow("the-web-layer-is-thin"));

    LessonRepository() {
      System.out.println("constructing LessonRepository");
    }

    List<LessonRow> findAll() {
      return rows;
    }
  }

  // One constructor, so the container has no choice to make and no @Autowired is
  // needed to tell it which one. The field is final: once the constructor returns,
  // the object is complete, and there is no window in which it exists half-built.
  static class LessonService {
    private final LessonRepository repository;

    LessonService(LessonRepository repository) {
      if (repository == null) {
        throw new IllegalArgumentException("repository is required");
      }
      this.repository = repository;
      System.out.println("constructing LessonService with a repository already in hand");
    }

    int count() {
      return repository.findAll().size();
    }
  }

  public static void main(String[] args) {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(LessonRepository.class, LessonService.class);
      context.refresh();

      LessonService first = context.getBean(LessonService.class);
      LessonService second = context.getBean(LessonService.class);
      System.out.println("lessons visible to the service: " + first.count());
      System.out.println("asked twice, same instance: " + (first == second));

      // The service is an ordinary object. Nothing prevents building one by hand,
      // which is exactly what a unit test does.
      LessonService byHand = new LessonService(new LessonRepository());
      System.out.println("built with new, no container: " + byHand.count());
    }
  }
}
