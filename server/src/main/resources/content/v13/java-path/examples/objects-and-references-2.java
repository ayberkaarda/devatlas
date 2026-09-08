final class IdentityAndEquality {

    public static void main(String[] args) {
        String literalA = "java";
        String literalB = "java";
        String built = new StringBuilder("ja").append("va").toString();

        System.out.println("literalA == literalB      : " + (literalA == literalB));
        System.out.println("literalA == built         : " + (literalA == built));
        System.out.println("literalA.equals(built)    : " + literalA.equals(built));
        System.out.println("built.intern() == literalA: " + (built.intern() == literalA));

        Integer small1 = 127;
        Integer small2 = 127;
        Integer large1 = 128;
        Integer large2 = 128;

        System.out.println("boxed 127 == 127          : " + (small1 == small2));
        System.out.println("boxed 128 == 128          : " + (large1 == large2));
        System.out.println("boxed 128 equals 128      : " + large1.equals(large2));

        Integer absent = null;
        try {
            int unboxed = absent;
            System.out.println(unboxed);
        } catch (NullPointerException e) {
            System.out.println("unboxing null             : NullPointerException");
        }
    }
}
