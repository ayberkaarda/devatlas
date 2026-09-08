// Spring Boot 4.1 (Spring Framework 7.0). @Transactional is applied by a proxy
// wrapped around the bean, so a call that never leaves the object never reaches it.
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

class SelfInvocationDemo {

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

  /** Its own separate bean, so the reference the container injects is the proxy. */
  static class AuditWriter {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void writeInItsOwnTransaction() {}
  }

  static class Tokens {
    private final AuditWriter auditWriter;

    Tokens(AuditWriter auditWriter) {
      this.auditWriter = auditWriter;
    }

    // The annotation is present and is silently ignored: this call is a direct
    // call on `this`, not a call through the proxy.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void writeOnThis() {}

    @Transactional
    void revokeThenSelfInvoke() {
      writeOnThis();
      throw new IllegalStateException("reuse detected");
    }

    @Transactional
    void revokeThenDelegate() {
      auditWriter.writeInItsOwnTransaction();
      throw new IllegalStateException("reuse detected");
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
    AuditWriter auditWriter() {
      return new AuditWriter();
    }

    @Bean
    Tokens tokens(AuditWriter auditWriter) {
      return new Tokens(auditWriter);
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
      Tokens tokens = context.getBean(Tokens.class);

      try {
        tokens.revokeThenSelfInvoke();
      } catch (IllegalStateException expected) {
        // The caller sees the exception either way.
      }
      System.out.println("self-invocation: " + manager.events);

      manager.events.clear();
      try {
        tokens.revokeThenDelegate();
      } catch (IllegalStateException expected) {
        // Same exception, same call order, different bean boundary.
      }
      System.out.println("separate bean:   " + manager.events);
    }
  }
}
