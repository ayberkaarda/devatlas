# include, extend and prepend all add a module to a lookup chain. They differ
# in which chain, and in where in it.

module Auditable
  def describe = "Auditable"
end

class Base
  def describe = "Base"
end

class Included < Base
  include Auditable

  def describe = "Included"
end

class Prepended < Base
  prepend Auditable

  def describe = "Prepended"
end

puts "Included.ancestors  -> #{Included.ancestors.inspect}"
puts "Included#describe   -> #{Included.new.describe}"
puts
puts "Prepended.ancestors -> #{Prepended.ancestors.inspect}"
puts "Prepended#describe  -> #{Prepended.new.describe}"

# Both classes define describe themselves. With include the class wins, because
# the module sits behind it; with prepend the module wins, because it sits in
# front. The ancestors list above says which, and it is the whole rule.
module Loud
  def describe = super.upcase + "!"
end

class Shouty < Base
  prepend Loud
end

puts
puts "Shouty.ancestors    -> #{Shouty.ancestors.inspect}"
puts "Shouty#describe     -> #{Shouty.new.describe}"

# extend adds the module to one object's own chain, so the methods become
# available on that object alone.
plain = Object.new
plain.extend(Auditable)
puts
puts "extended object     -> #{plain.describe}"
puts "singleton has it    -> #{plain.singleton_class.ancestors.include?(Auditable)}"
puts "Object does not     -> #{Object.new.respond_to?(:describe)}"

# extend on a class object is how a module contributes class methods.
class Registry
  extend Auditable
end
puts "Registry.describe   -> #{Registry.describe}"
puts "instance has it     -> #{Registry.new.respond_to?(:describe)}"

# Modules are not classes and cannot be instantiated.
begin
  Auditable.new
rescue NoMethodError => e
  puts
  puts "Auditable.new       -> #{e.class}: #{e.message}"
end
