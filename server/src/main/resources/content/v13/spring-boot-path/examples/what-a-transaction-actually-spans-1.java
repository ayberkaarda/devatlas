// Spring Boot 4.1 (Spring Framework 7.0). What a transaction spans, made visible:
// a transaction manager that records every begin, suspend, resume, commit and
// rollback instead of talking to a database.
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class TransactionPropagationDemo {

  /** A stand-in for a real transaction manager: it records instead of connecting. */
  static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
    final List<String> events = new ArrayList<>();
    private int counter;
    private String active;

    static final class Holder {
      String name;
    }

    @Override
    protected Object doGetTransaction() {
      Holder holder = new Holder();
      holder.name = active;
      return holder;
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
      return ((Holder) transaction).name != null;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      active = "t" + (++counter);
      ((Holder) transaction).name = active;
      events.add("begin " + active);
    }

    @Override
    protected Object doSuspend(Object transaction) {
      String suspended = active;
      active = null;
      ((Holder) transaction).name = null;
      events.add("suspend " + suspended);
      return suspended;
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
      active = (String) suspendedResources;
      events.add("resume " + active);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      events.add("commit " + ((Holder) status.getTransaction()).name);
      active = null;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      events.add("rollback " + ((Holder) status.getTransaction()).name);
      active = null;
    }
  }

  static class Auditor {
    // REQUIRED is the default: join the caller's transaction if there is one.
    @Transactional(propagation = Propagation.REQUIRED)
    void recordJoining() {}

    // REQUIRES_NEW suspends the caller's transaction and runs in its own, which
    // commits before control returns.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void recordSeparately() {}
  }

  static class Workflow {
    private final Auditor auditor;

    Workflow(Auditor auditor) {
      this.auditor = auditor;
    }

    @Transactional
    void withJoiningAudit() {
      auditor.recordJoining();
    }

    @Transactional
    void withSeparateAudit() {
      auditor.recordSeparately();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableTransactionManagement
  static class DemoConfiguration {
    @Bean
    RecordingTransactionManager transactionManager() {
      return new RecordingTransactionManager();
    }

    @Bean
    Auditor auditor() {
      return new Auditor();
    }

    @Bean
    Workflow workflow(Auditor auditor) {
      return new Workflow(auditor);
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  public static void main(String[] args) {
    quietLogging();

    try (var context = new AnnotationConfigApplicationContext(DemoConfiguration.class)) {
      RecordingTransactionManager manager = context.getBean(RecordingTransactionManager.class);
      Workflow workflow = context.getBean(Workflow.class);

      workflow.withJoiningAudit();
      System.out.println("REQUIRED:     " + manager.events);

      manager.events.clear();
      workflow.withSeparateAudit();
      System.out.println("REQUIRES_NEW: " + manager.events);
    }
  }
}
