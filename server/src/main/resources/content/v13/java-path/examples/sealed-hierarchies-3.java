final class WhatSealingBuys {

    // An open hierarchy: the dispatch below has to guess about anything it has not seen.
    interface OpenCommand { }

    record OpenStart(String job) implements OpenCommand { }

    record OpenStop(String job) implements OpenCommand { }

    record OpenPause(String job) implements OpenCommand { }   // added later

    static String handleOpen(OpenCommand command) {
        if (command instanceof OpenStart s) {
            return "start " + s.job();
        } else if (command instanceof OpenStop s) {
            return "stop " + s.job();
        }
        return "ignored";                                     // silently swallows OpenPause
    }

    // A sealed hierarchy: adding a case is a compile error until it is handled.
    sealed interface Command permits Start, Stop, Pause { }

    record Start(String job) implements Command { }

    record Stop(String job) implements Command { }

    record Pause(String job) implements Command { }

    static String handle(Command command) {
        return switch (command) {
            case Start s -> "start " + s.job();
            case Stop s -> "stop " + s.job();
            case Pause p -> "pause " + p.job();
        };
    }

    public static void main(String[] args) {
        System.out.println("open   : " + handleOpen(new OpenStart("import")));
        System.out.println("open   : " + handleOpen(new OpenStop("import")));
        System.out.println("open   : " + handleOpen(new OpenPause("import")));

        System.out.println("sealed : " + handle(new Start("import")));
        System.out.println("sealed : " + handle(new Stop("import")));
        System.out.println("sealed : " + handle(new Pause("import")));
    }
}
