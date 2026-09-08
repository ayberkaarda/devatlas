# Every value in Ruby answers to #class, and every class is itself a value.

puts "3 is a #{3.class}"
puts "3.7 is a #{3.7.class}"
puts "\"text\" is a #{"text".class}"
puts ":name is a #{:name.class}"
puts "[] is a #{[].class}"
puts "nil is a #{nil.class}"
puts "true is a #{true.class}"

# A class is an object too: it has a class of its own, and that chain terminates
# at Class, which is its own class.
puts
puts "Integer.class  -> #{Integer.class}"
puts "NilClass.class -> #{NilClass.class}"
puts "Class.class    -> #{Class.class}"

# The ancestors list is the search path a method call walks. Notice that
# NilClass shares most of it with every other class.
puts
puts "NilClass.ancestors -> #{NilClass.ancestors.inspect}"
puts "Integer.ancestors  -> #{Integer.ancestors.inspect}"
puts "shared tail        -> #{(NilClass.ancestors & Integer.ancestors).inspect}"

# Because nil is an ordinary object, asking it questions is ordinary too.
puts
puts "nil.respond_to?(:to_a) -> #{nil.respond_to?(:to_a)}"
puts "nil.nil?               -> #{nil.nil?}"
puts "3.nil?                 -> #{3.nil?}"
puts "nil.is_a?(Object)      -> #{nil.is_a?(Object)}"

# nil is a singleton: there is exactly one of it, and it cannot be constructed.
puts
puts "nil.equal?(nil) -> #{nil.equal?(nil)}"
begin
  NilClass.new
rescue NoMethodError => e
  puts "NilClass.new    -> #{e.class}: #{e.message}"
end
puts "nil.frozen?     -> #{nil.frozen?}"
