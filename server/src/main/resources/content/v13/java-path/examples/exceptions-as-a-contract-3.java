final class SwallowedFailures {

    // A return inside finally discards the exception that was on its way out.
    @SuppressWarnings("finally")
    static int swallowing() {
        try {
            throw new IllegalStateException("this never reaches the caller");
        } finally {
            return -1;
        }
    }

    static int notSwallowing() {
        try {
            throw new IllegalStateException("this does reach the caller");
        } finally {
            System.out.println("  finally ran either way");
        }
    }

    static void discardsTheCause() {
        try {
            Integer.parseInt("x");
        } catch (NumberFormatException e) {
            throw new IllegalStateException("could not read the setting");
        }
    }

    static void keepsTheCause() {
        try {
            Integer.parseInt("x");
        } catch (NumberFormatException e) {
            throw new IllegalStateException("could not read the setting", e);
        }
    }

    public static void main(String[] args) {
        System.out.println("swallowing returned : " + swallowing());

        try {
            notSwallowing();
        } catch (IllegalStateException e) {
            System.out.println("propagated          : " + e.getMessage());
        }

        try {
            discardsTheCause();
        } catch (IllegalStateException e) {
            System.out.println("cause discarded     : " + e.getCause());
        }

        try {
            keepsTheCause();
        } catch (IllegalStateException e) {
            System.out.println("cause kept          : " + e.getCause().getClass().getSimpleName()
                    + ": " + e.getCause().getMessage());
        }
    }
}
