# What nil offers, and what it refuses.

# nil converts, so a missing value can join a computation without a branch.
puts "nil.to_s.inspect -> #{nil.to_s.inspect}"
puts "nil.to_a.inspect -> #{nil.to_a.inspect}"
puts "nil.to_i         -> #{nil.to_i}"
puts "nil.to_h.inspect -> #{nil.to_h.inspect}"
puts "nil.inspect      -> #{nil.inspect}"

# Those conversions let a caller treat "absent" as "empty" deliberately.
tags = nil
puts "splatted        -> #{[*tags, "ruby"].inspect}"
puts "joined          -> #{Array(tags).join(", ").inspect}"

# What nil refuses is everything else, and it says so by name.
begin
  nil.upcase
rescue NoMethodError => e
  puts "nil.upcase      -> #{e.class}: #{e.message}"
end

# Safe navigation short-circuits the call and yields nil instead of raising.
name = nil
puts "name&.upcase    -> #{name&.upcase.inspect}"
name = "ada"
puts "name&.upcase    -> #{name&.upcase.inspect}"

# A chain of hash lookups is the usual source of an unwanted nil. #dig walks
# the chain and stops at the first missing step rather than raising.
config = { server: { host: "localhost" } }
puts "dig present     -> #{config.dig(:server, :host).inspect}"
puts "dig absent      -> #{config.dig(:server, :port).inspect}"
puts "dig missing top -> #{config.dig(:client, :host).inspect}"

begin
  config[:client][:host]
rescue NoMethodError => e
  puts "[][] absent     -> #{e.class}: #{e.message}"
end
