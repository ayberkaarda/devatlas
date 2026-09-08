// Java 21. What an @Enumerated(EnumType.ORDINAL) column actually stores, and what
// happens to rows already holding those numbers when the enum grows a constant.
class EnumOrdinalDemo {

  // The enum as it stood when the rows were written.
  enum DifficultyV1 {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
  }

  // The same enum after someone added a level at the front, which reads as a
  // harmless edit and changes every ordinal after it.
  enum DifficultyV2 {
    INTRODUCTORY,
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
  }

  public static void main(String[] args) {
    // ORDINAL writes Enum.ordinal(); STRING writes Enum.name().
    int storedOrdinal = DifficultyV1.ADVANCED.ordinal();
    String storedName = DifficultyV1.ADVANCED.name();
    System.out.println("row written with the first enum: ordinal=" + storedOrdinal + " name=" + storedName);

    // The row is untouched. Only the Java type changed.
    System.out.println("same number read back through the second enum: " + DifficultyV2.values()[storedOrdinal]);
    System.out.println("same name read back through the second enum:   " + DifficultyV2.valueOf(storedName));

    System.out.println("--- ordinals before and after the edit ---");
    for (DifficultyV1 value : DifficultyV1.values()) {
      System.out.println("  " + value.name() + " was " + value.ordinal());
    }
    for (DifficultyV2 value : DifficultyV2.values()) {
      System.out.println("  " + value.name() + " is now " + value.ordinal());
    }

    // A name that no longer exists fails loudly. A number that no longer means what
    // it meant does not.
    try {
      DifficultyV1.valueOf("INTRODUCTORY");
    } catch (IllegalArgumentException refused) {
      System.out.println("a removed name is refused: " + refused.getClass().getSimpleName());
    }
  }
}
