// Spring Data JPA 4.1 (Spring Boot 4.1). The name resolution algorithm takes the
// longest match first, which is how a method name can bind to the wrong property.
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

class DerivedQueryAmbiguityDemo {

  static class Address {
    private String zipCode;

    public String getZipCode() {
      return zipCode;
    }
  }

  static class Person {
    private String name;
    private Address address;
    // Added later, by someone who had never read the repository interface.
    private String addressZip;

    public String getName() {
      return name;
    }

    public Address getAddress() {
      return address;
    }

    public String getAddressZip() {
      return addressZip;
    }
  }

  static void explain(String methodName) {
    try {
      PartTree tree = new PartTree(methodName, Person.class);
      for (Part part : tree.getParts()) {
        System.out.println(methodName + " -> " + part.getProperty());
      }
    } catch (PropertyReferenceException refused) {
      System.out.println(methodName + " -> refused: " + refused.getClass().getSimpleName());
      System.out.println("   " + refused.getMessage().split("\\R")[0]);
    }
  }

  public static void main(String[] args) {
    // Unambiguous: nothing else in Person starts with these letters.
    explain("findByName");
    // The whole part matches the flat property first, so the nested path is never
    // considered -- and the type of that property has no `code` beneath it.
    explain("findByAddressZipCode");
    // The underscore names the traversal point explicitly and settles it.
    explain("findByAddress_ZipCode");
  }
}
