import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WhatFinalMeans {

    static final class Config {
        private final List<String> hosts = new ArrayList<>();

        void add(String host) {
            hosts.add(host);
        }

        @Override
        public String toString() {
            return hosts.toString();
        }
    }

    public static void main(String[] args) {
        Config config = new Config();
        config.add("a.example");
        config.add("b.example");
        System.out.println("final field, changed object : " + config);

        final int[] counter = { 0 };
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            tasks.add(() -> counter[0]++);
        }
        tasks.forEach(Runnable::run);
        System.out.println("final array, changed content: " + counter[0]);

        final String greeting = "hello";
        String shouted = greeting.toUpperCase(Locale.ROOT);
        System.out.println("String returns a new object : " + shouted + " / " + greeting);
        System.out.println("same object                 : " + (shouted == greeting));
    }
}
