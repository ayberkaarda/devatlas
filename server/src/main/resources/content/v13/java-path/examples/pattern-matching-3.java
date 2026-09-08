final class ExpressionEvaluator {

    sealed interface Expr permits Literal, Add, Multiply, Negate { }

    record Literal(int value) implements Expr { }

    record Add(Expr left, Expr right) implements Expr { }

    record Multiply(Expr left, Expr right) implements Expr { }

    record Negate(Expr operand) implements Expr { }

    static int eval(Expr expr) {
        return switch (expr) {
            case Literal(int value) -> value;
            case Add(Expr l, Expr r) -> eval(l) + eval(r);
            case Multiply(Expr l, Expr r) -> eval(l) * eval(r);
            case Negate(Expr operand) -> -eval(operand);
        };
    }

    // Nested patterns let one label recognise a whole shape.
    // A more specific nested pattern must come before the general one.
    static Expr simplify(Expr expr) {
        return switch (expr) {
            case Literal l -> l;
            case Negate(Negate(Expr inner)) -> simplify(inner);
            case Negate(Expr operand) -> new Negate(simplify(operand));
            case Multiply(Literal(int a), Expr r) when a == 1 -> simplify(r);
            case Multiply(Expr l, Literal(int b)) when b == 1 -> simplify(l);
            case Multiply(Expr l, Expr r) -> new Multiply(simplify(l), simplify(r));
            case Add(Literal(int a), Literal(int b)) -> new Literal(a + b);
            case Add(Expr l, Expr r) -> new Add(simplify(l), simplify(r));
        };
    }

    static String print(Expr expr) {
        return switch (expr) {
            case Literal(int value) -> Integer.toString(value);
            case Add(Expr l, Expr r) -> "(" + print(l) + " + " + print(r) + ")";
            case Multiply(Expr l, Expr r) -> "(" + print(l) + " * " + print(r) + ")";
            case Negate(Expr operand) -> "-" + print(operand);
        };
    }

    public static void main(String[] args) {
        Expr e = new Add(new Multiply(new Literal(1), new Literal(6)),
                         new Negate(new Negate(new Literal(4))));

        System.out.println("printed   : " + print(e));
        System.out.println("evaluated : " + eval(e));
        Expr once = simplify(e);
        System.out.println("one pass  : " + print(once));
        System.out.println("two passes: " + print(simplify(once)));
        System.out.println("2*(3+4)   : " + eval(new Multiply(new Literal(2),
                new Add(new Literal(3), new Literal(4)))));
    }
}
