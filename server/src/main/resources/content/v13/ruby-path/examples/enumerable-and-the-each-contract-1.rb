# Enumerable is the bargain at the centre of Ruby's collections: define each,
# and roughly sixty methods arrive with it.

class Shelf
  include Enumerable

  def initialize(*books) = @books = books

  # The whole contract. Enumerable calls this and nothing else.
  def each
    return to_enum(:each) unless block_given?

    @books.each { |book| yield book }
    self
  end
end

shelf = Shelf.new("Dune", "Emma", "Ada", "Ulysses")

puts "sort         -> #{shelf.sort.inspect}"
puts "map          -> #{shelf.map(&:length).inspect}"
puts "select       -> #{shelf.select { it.length == 4 }.inspect}"
puts "reject       -> #{shelf.reject { it.length == 4 }.inspect}"
puts "min_by       -> #{shelf.min_by(&:length)}"
# Ruby does not promise a stable sort, so the key is made unique rather than
# relying on the order two equal-length titles happen to come out in.
puts "sort_by      -> #{shelf.sort_by { [it.length, it] }.inspect}"
puts "group_by     -> #{shelf.group_by(&:length).inspect}"
puts "partition    -> #{shelf.partition { it.start_with?("D", "E") }.inspect}"
puts "each_slice   -> #{shelf.each_slice(2).to_a.inspect}"
puts "include?     -> #{shelf.include?("Emma")}"
puts "first(2)     -> #{shelf.first(2).inspect}"
puts "reduce       -> #{shelf.reduce(0) { |sum, b| sum + b.length }}"
puts "each_with_object -> #{shelf.each_with_object({}) { |b, h| h[b[0]] = b.length }.inspect}"
puts "tally by size    -> #{shelf.map(&:length).tally.inspect}"

# Not one of those was written above.
puts
puts "written here     -> #{Shelf.instance_methods(false).sort.inspect}"
puts "arrived from mixin -> #{Enumerable.instance_methods.size > 50}"

# Without each, the module has nothing to call, and the failure names the
# missing method rather than the missing include.
class Empty
  include Enumerable
end

begin
  Empty.new.map { it }
rescue NoMethodError => e
  puts
  puts "no each defined  -> #{e.class}: #{e.message}"
end
