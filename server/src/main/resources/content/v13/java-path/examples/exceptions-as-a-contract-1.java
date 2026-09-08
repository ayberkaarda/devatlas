final class CheckedAndUnchecked {

    // Checked: the signature is part of the contract, and callers must answer it.
    static final class ConfigurationException extends Exception {
        ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static int parsePort(String raw) throws ConfigurationException {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            // Unchecked in, checked out: the caller can do something about this one.
            throw new ConfigurationException("port is not a number: " + raw, e);
        }
    }

    static void requireInRange(int port) {
        if (port < 1 || port > 65535) {
            // Unchecked: a caller passing 0 has a bug, not a situation.
            throw new IllegalArgumentException("port out of range: " + port);
        }
    }

    public static void main(String[] args) {
        System.out.println("Exception is checked  : " + isChecked(new Exception()));
        System.out.println("RuntimeException      : " + isChecked(new RuntimeException()));
        System.out.println("Error                 : " + isChecked(new StackOverflowError()));

        try {
            System.out.println("parsed                : " + parsePort("8080"));
        } catch (ConfigurationException e) {
            System.out.println("unreachable");
        }

        // Option one: handle it and carry on with a default.
        int port;
        try {
            port = parsePort("eighty");
        } catch (ConfigurationException e) {
            System.out.println("caught                : " + e.getMessage());
            System.out.println("cause preserved       : " + e.getCause().getClass().getSimpleName());
            port = 8080;
        }
        System.out.println("fell back to          : " + port);

        try {
            requireInRange(0);
        } catch (IllegalArgumentException e) {
            System.out.println("programming error     : " + e.getMessage());
        }
    }

    static boolean isChecked(Throwable t) {
        return !(t instanceof RuntimeException) && !(t instanceof Error);
    }
}
