final class ResourcesAndSuppression {

    static final class Noisy implements AutoCloseable {
        private final String name;
        private final boolean failOnClose;

        Noisy(String name, boolean failOnClose) {
            this.name = name;
            this.failOnClose = failOnClose;
            System.out.println("  opened " + name);
        }

        void use() {
            System.out.println("  used " + name);
        }

        @Override
        public void close() {
            System.out.println("  closing " + name);
            if (failOnClose) {
                throw new IllegalStateException("close failed for " + name);
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("resources close in reverse order:");
        try (Noisy first = new Noisy("first", false);
             Noisy second = new Noisy("second", false)) {
            first.use();
            second.use();
        }

        System.out.println("a failure in the body wins; close failures are suppressed:");
        try (Noisy resource = new Noisy("resource", true)) {
            resource.use();
            throw new IllegalArgumentException("the body failed");
        } catch (RuntimeException e) {
            System.out.println("caught     : " + e.getClass().getSimpleName() + ": " + e.getMessage());
            for (Throwable suppressed : e.getSuppressed()) {
                System.out.println("suppressed : " + suppressed.getClass().getSimpleName()
                        + ": " + suppressed.getMessage());
            }
        }

        System.out.println("with no body failure, the close failure is thrown:");
        try (Noisy resource = new Noisy("resource", true)) {
            resource.use();
        } catch (RuntimeException e) {
            System.out.println("caught     : " + e.getClass().getSimpleName() + ": " + e.getMessage());
            System.out.println("suppressed count: " + e.getSuppressed().length);
        }
    }
}
