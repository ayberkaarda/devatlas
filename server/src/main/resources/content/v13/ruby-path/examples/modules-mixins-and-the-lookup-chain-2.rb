# A mixin is a bargain: you supply one method, the module supplies many. The
# two mixins in the standard library that make this bargain are Comparable,
# which wants <=>, and Enumerable, which wants each.

class Version
  include Comparable

  attr_reader :major, :minor, :patch

  def initialize(text)
    @major, @minor, @patch = text.split(".").map(&:to_i)
  end

  # The one method the bargain requires.
  def <=>(other) = [major, minor, patch] <=> [other.major, other.minor, other.patch]

  def to_s = "#{major}.#{minor}.#{patch}"
  def inspect = "#<Version #{self}>"
end

a = Version.new("3.4.10")
b = Version.new("3.4.9")
c = Version.new("3.5.0")

puts "a <=> b      -> #{(a <=> b)}"
puts "a > b        -> #{a > b}"
puts "a < c        -> #{a < c}"
puts "a == a2      -> #{a == Version.new("3.4.10")}"
puts "between?     -> #{a.between?(b, c)}"
puts "clamp        -> #{a.clamp(b, c)}"
puts "sorted       -> #{[c, a, b].sort.map(&:to_s).inspect}"
puts "max          -> #{[c, a, b].max}"

# None of those seven methods were written above. They arrive from the module.
puts
puts "from Comparable -> #{(Comparable.instance_methods).sort.inspect}"
puts "Version has <=>  -> #{Version.instance_methods(false).sort.inspect}"

# Note that 3.4.10 sorts after 3.4.9, which string comparison would get wrong.
puts
puts "as strings      -> #{["3.4.10", "3.4.9"].sort.inspect}"
puts "as versions     -> #{[a, b].sort.map(&:to_s).inspect}"

# The bargain fails loudly if the required method is missing.
class Broken
  include Comparable
end

begin
  Broken.new < Broken.new
rescue ArgumentError => e
  puts
  puts "no <=> defined  -> #{e.class}: #{e.message}"
end
