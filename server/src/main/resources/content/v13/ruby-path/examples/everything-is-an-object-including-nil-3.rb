# A class you write sits on the same chain as the ones that ship with Ruby,
# so the same mechanisms apply to it without ceremony.

class Money
  include Comparable

  attr_reader :cents

  def initialize(cents)
    @cents = cents
    freeze
  end

  # One operator, and Comparable supplies <, <=, >, >=, ==, between?, clamp.
  def <=>(other) = cents <=> other.cents

  def to_s = format("%.2f", cents / 100.0)

  # Without this, inspect would print an internal address, which is different
  # on every run and therefore useless in a recorded output.
  def inspect = "#<Money #{self}>"
end

a = Money.new(250)
b = Money.new(999)

puts "a            -> #{a}"
puts "a.inspect    -> #{a.inspect}"
puts "a < b        -> #{a < b}"
puts "a.between?   -> #{a.between?(Money.new(100), Money.new(300))}"
puts "sorted       -> #{[b, a].sort.map(&:to_s).inspect}"
puts "min          -> #{[b, a].min}"

# The chain is visible and ordinary.
puts
puts "Money.ancestors -> #{Money.ancestors.inspect}"
puts "a.is_a?(Object) -> #{a.is_a?(Object)}"
puts "a.class.class   -> #{a.class.class}"

# freeze in the constructor makes the object refuse mutation. The refusal is an
# exception with a name, not a silent copy.
puts
puts "a.frozen?       -> #{a.frozen?}"
begin
  a.instance_variable_set(:@cents, 1)
rescue FrozenError => e
  puts "mutation        -> #{e.class}: #{e.message}"
end
