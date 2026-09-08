# What "frozen" costs and buys, measured rather than remembered.

def row(label, value) = puts(format("%-28s -> %s", label, value))

# A string literal in a file without the frozen_string_literal comment is not
# frozen in Ruby 3.4, so it can be mutated in place.
greeting = "hello"
row('literal frozen?', greeting.frozen?)
greeting << " there"
row('after <<', greeting.inspect)

# Freezing it explicitly turns the same mutation into a named exception.
frozen = "hello".freeze
row('after freeze', frozen.frozen?)
begin
  frozen << " there"
rescue FrozenError => e
  row('mutating a frozen String', "#{e.class}: #{e.message}")
end

# The two unary operators ask for the version you want: +str gives a mutable
# copy of a frozen string, -str gives a frozen, deduplicated one.
puts
row('(+frozen).frozen?', (+frozen).frozen?)
row('(-greeting).frozen?', (-"unique-name").frozen?)
row('-str deduplicates', (-"dedup").equal?(-"dedup"))
row('+str is a new object', (+frozen).equal?(frozen))

# Shared mutable state is the reason this matters. A default argument is
# evaluated on every call, so each caller gets its own array; a constant is
# evaluated once, so every caller shares one.
DEFAULT_TAGS = ["ruby"]

def with_default(tags = ["ruby"])
  tags << "new"
end

def with_constant(tags = DEFAULT_TAGS)
  tags << "new"
end

puts
row('default arg, call 1', with_default.inspect)
row('default arg, call 2', with_default.inspect)
row('constant, call 1', with_constant.inspect)
row('constant, call 2', with_constant.inspect)
row('constant now holds', DEFAULT_TAGS.inspect)

# Freezing the constant makes the second call fail instead of accumulating.
SAFE_TAGS = ["ruby"].freeze

def with_frozen_constant(tags = SAFE_TAGS)
  tags << "new"
end

begin
  with_frozen_constant
rescue FrozenError => e
  puts
  row('frozen constant', "#{e.class}: #{e.message}")
end

# freeze is shallow: the array is frozen, the strings inside it are not.
NESTED = ["ruby"].freeze
row('NESTED.frozen?', NESTED.frozen?)
row('NESTED[0].frozen?', NESTED[0].frozen?)
NESTED[0] << "!"
row('inner mutated anyway', NESTED.inspect)
