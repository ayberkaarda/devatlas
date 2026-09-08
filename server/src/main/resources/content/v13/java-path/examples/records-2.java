import java.util.Locale;

final class RecordInvariants {

    record Money(String currency, long minorUnits) {

        // Compact constructor: validate, then normalise by assigning the parameter.
        Money {
            if (currency == null || currency.length() != 3) {
                throw new IllegalArgumentException("currency must be three letters, was: " + currency);
            }
            if (minorUnits < 0) {
                throw new IllegalArgumentException("minorUnits must not be negative, was: " + minorUnits);
            }
            currency = currency.toUpperCase(Locale.ROOT);
        }

        static Money euros(long minorUnits) {
            return new Money("eur", minorUnits);
        }

        Money plus(Money other) {
            if (!currency.equals(other.currency)) {
                throw new IllegalArgumentException("cannot add " + other.currency + " to " + currency);
            }
            return new Money(currency, minorUnits + other.minorUnits);
        }
    }

    public static void main(String[] args) {
        Money a = Money.euros(250);
        System.out.println("normalised : " + a);
        System.out.println("sum        : " + a.plus(new Money("EUR", 125)));
        System.out.println("equal      : " + a.equals(new Money("eUr", 250)));

        try {
            new Money("EURO", 1);
        } catch (IllegalArgumentException e) {
            System.out.println("rejected   : " + e.getMessage());
        }

        try {
            new Money("EUR", -1);
        } catch (IllegalArgumentException e) {
            System.out.println("rejected   : " + e.getMessage());
        }

        try {
            a.plus(new Money("USD", 1));
        } catch (IllegalArgumentException e) {
            System.out.println("rejected   : " + e.getMessage());
        }
    }
}
