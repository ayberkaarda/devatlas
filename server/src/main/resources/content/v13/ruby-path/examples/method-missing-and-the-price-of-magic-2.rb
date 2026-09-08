# The repair is respond_to_missing?, which teaches reflection the same rule
# method_missing uses. Define the two together or neither.

class Row
  def initialize(fields) = @fields = fields

  def method_missing(name, *args)
    key = name.to_s
    return @fields[key] if @fields.key?(key)

    super
  end

  def respond_to_missing?(name, include_private = false)
    @fields.key?(name.to_s) || super
  end
end

row = Row.new({ "title" => "Blocks", "words" => 640 })

puts "respond_to?(:title)  -> #{row.respond_to?(:title)}"
puts "respond_to?(:author) -> #{row.respond_to?(:author)}"

# method now succeeds and hands back a callable object.
m = row.method(:title)
puts "method(:title).class -> #{m.class}"
puts "method(:title).call  -> #{m.call.inspect}"
puts "method name          -> #{m.name.inspect}"

def render(obj)
  obj.respond_to?(:title) ? "title: #{obj.title}" : "no title available"
end
puts "render(row)          -> #{render(row)}"

# Still absent from #methods, because nothing was ever defined. respond_to?
# and #methods answer different questions and only one of them is patched.
puts "methods include?     -> #{row.methods.include?(:title)}"

# The alternative: define the methods for real at class-definition time. The
# object then answers every reflective question without a special case.
class Defined
  def self.field(name)
    define_method(name) { @fields[name.to_s] }
  end

  field :title
  field :words

  def initialize(fields) = @fields = fields
end

d = Defined.new({ "title" => "Blocks", "words" => 640 })
puts
puts "defined title        -> #{d.title.inspect}"
puts "respond_to?          -> #{d.respond_to?(:title)}"
puts "methods include?     -> #{d.methods.include?(:title)}"
puts "instance_methods     -> #{Defined.instance_methods(false).sort.inspect}"
puts "method object        -> #{d.method(:title).call.inspect}"

begin
  d.author
rescue NoMethodError => e
  puts "unknown name         -> #{e.class}: #{e.message}"
end
