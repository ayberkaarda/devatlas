# method_missing is the last step of method lookup. When nothing in the chain
# answers a call, Ruby calls method_missing with the name and the arguments.

class Row
  def initialize(fields) = @fields = fields

  def method_missing(name, *args)
    key = name.to_s
    return @fields[key] if @fields.key?(key)

    # Anything this object does not handle must go back to the default, which
    # is what raises NoMethodError.
    super
  end
end

row = Row.new({ "title" => "Blocks", "words" => 640 })

puts "row.title            -> #{row.title.inspect}"
puts "row.words            -> #{row.words.inspect}"

# The name arrives as a Symbol, and unmatched names still raise, because of super.
begin
  row.author
rescue NoMethodError => e
  puts "row.author           -> #{e.class}: #{e.message}"
end

# The price, part one: the object lies about itself. Nothing was defined, so
# every reflective question gives the wrong answer.
puts
puts "respond_to?(:title)  -> #{row.respond_to?(:title)}"
puts "methods include?     -> #{row.methods.include?(:title)}"
puts "public_send works    -> #{row.public_send(:title).inspect}"

begin
  row.method(:title)
rescue NameError => e
  puts "method(:title)       -> #{e.class}: #{e.message}"
end

# The price, part two: duck typing is built on respond_to?, so anything that
# asks before calling will skip this object.
def render(obj)
  obj.respond_to?(:title) ? "title: #{obj.title}" : "no title available"
end

puts
puts "render(row)          -> #{render(row)}"
