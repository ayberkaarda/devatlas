# A String is a container of characters. A Symbol is a name. The difference
# shows up in identity, in mutability and in what each one is for.

def row(label, value) = puts(format("%-25s -> %s", label, value))

# Two occurrences of the same symbol literal are the same object. Two
# occurrences of the same string literal are not.
row(':name.equal?(:name)', :name.equal?(:name))
row('"name".equal?("name")', "name".equal?("name"))
row(':name == :name', :name == :name)
row('"name" == "name"', "name" == "name")

# A symbol is frozen. In Ruby 3.4 a plain string literal is not.
puts
row(':name.frozen?', :name.frozen?)
row('"name".frozen?', "name".frozen?)
row('1.frozen?', 1.frozen?)
row('nil.frozen?', nil.frozen?)
row('(1..3).frozen?', (1..3).frozen?)
row('[].frozen?', [].frozen?)
row('{}.frozen?', {}.frozen?)

# So a string can be built up in place, and a symbol cannot be changed at all.
buffer = +"na"
buffer << "me"
puts
row('built string', buffer.inspect)
row('upcase! on String', buffer.upcase!.inspect)

begin
  :name.upcase!
rescue NoMethodError => e
  row('upcase! on Symbol', "#{e.class}: #{e.message}")
end

# Symbol still has the non-mutating half of the String interface, and it
# returns a Symbol.
row(':name.upcase', :name.upcase.inspect)
row(':name.length', :name.length)
row(':name.start_with?("na")', :name.start_with?("na"))

# Conversion is exact in both directions, and a converted symbol is the same
# object as the literal.
puts
row(':name.to_s', :name.to_s.inspect)
row('"name".to_sym', "name".to_sym.inspect)
row('round trip identical', "name".to_sym.equal?(:name))
row(':name == "name"', :name == "name")
