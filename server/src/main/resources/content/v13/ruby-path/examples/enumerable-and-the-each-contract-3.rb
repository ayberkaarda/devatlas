# An Enumerator is an each that has not run yet. It is what an iterator returns
# when you do not give it a block, and it is how a sequence can be infinite.

e = [10, 20, 30].each
puts "each without block -> #{e.class}"
puts "next               -> #{e.next}"
puts "next               -> #{e.next}"
puts "next               -> #{e.next}"
begin
  e.next
rescue StopIteration => ex
  puts "past the end       -> #{ex.class}: #{ex.message}"
end

# Enumerator.new takes a block that is handed a yielder. Nothing in it runs
# until something asks for a value.
side_effects = []
counted = Enumerator.new do |y|
  3.times do |i|
    side_effects << i
    y << i * i
  end
end

puts
puts "before consuming   -> #{side_effects.inspect}"
puts "first(2)           -> #{counted.first(2).inspect}"
puts "after first(2)     -> #{side_effects.inspect}"

# lazy makes a chain of Enumerable calls pull one element at a time instead of
# building a full intermediate array at every step. That is what lets it run
# over an endless range.
naturals = (1..Float::INFINITY)
puts
puts "lazy squares       -> #{naturals.lazy.select(&:even?).map { it * it }.first(4).inspect}"
puts "lazy is lazy       -> #{naturals.lazy.map { it * 2 }.class}"
puts "take then force    -> #{naturals.lazy.map { it * 3 }.take(3).force.inspect}"

# The same chain without lazy would never return, so the difference is not an
# optimisation but the difference between working and not. Counting the block
# invocations shows why.
calls_eager = 0
(1..20).select { calls_eager += 1; it.even? }.map { it * it }.first(2)
calls_lazy = 0
(1..20).lazy.select { calls_lazy += 1; it.even? }.map { it * it }.first(2)

puts
puts "eager block calls  -> #{calls_eager}"
puts "lazy block calls   -> #{calls_lazy}"

# to_enum lets a method with its own iteration hand back an Enumerator, so the
# caller gets external iteration and the whole Enumerable interface for free.
class Countdown
  include Enumerable

  def initialize(from) = @from = from

  def each
    return to_enum(:each) { @from } unless block_given?

    @from.downto(1) { |n| yield n }
  end
end

c = Countdown.new(4)
puts
puts "with a block       -> #{c.map { it }.inspect}"
puts "without a block    -> #{c.each.class}"
puts "size known ahead   -> #{c.each.size}"
# Each call to #each builds a fresh Enumerator, so hold on to one if you want
# external iteration to advance rather than restart.
iter = c.each
puts "external, kept     -> #{[iter.next, iter.next].inspect}"
puts "external, fresh    -> #{[c.each.next, c.each.next].inspect}"
puts "with_index         -> #{c.each_with_index.to_a.inspect}"
