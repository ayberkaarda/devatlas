# Hash keys are where the choice bites, because a Hash compares keys with eql?
# and a symbol never equals a string.

def row(label, value) = puts(format("%-26s -> %s", label, value))

record = { name: "Ada", "name" => "Grace" }
row('two distinct keys', record.size)
row('symbol key', record[:name].inspect)
row('string key', record["name"].inspect)
row('inspect', record.inspect)

# Data that crossed a boundary arrives with string keys. Reaching for a symbol
# then returns nil rather than raising, which is how the mistake travels.
parsed = { "name" => "Ada", "roles" => ["admin"] }
puts
row('parsed[:name]', parsed[:name].inspect)
row('parsed["name"]', parsed["name"].inspect)
row('after transform_keys', parsed.transform_keys(&:to_sym).inspect)

# fetch turns the silent nil into a named failure.
begin
  parsed.fetch(:name)
rescue KeyError => e
  row('fetch(:name)', "#{e.class}: #{e.message}")
end

# A Hash presents its entries in the order they were created, which Ruby
# specifies rather than leaves to the implementation. That is why printing a
# Hash here records a property and not an accident.
scores = {}
scores[:zoe] = 1
scores[:ada] = 2
scores[:bob] = 3
puts
row('insertion order', scores.keys.inspect)
row('reassign keeps place', (scores[:zoe] = 9; scores.keys.inspect))
scores.delete(:zoe)
scores[:zoe] = 1
row('delete then re-add', scores.keys.inspect)
row('sorted instead', scores.sort.to_h.keys.inspect)

# Symbol#to_proc is why &:method reads the way it does: it builds a proc that
# sends the symbol to its argument.
puts
row('map(&:upcase)', %w[a b].map(&:upcase).inspect)
row(':upcase.to_proc.call', :upcase.to_proc.call("a").inspect)
row('to_proc is a Proc', :upcase.to_proc.class)
row('sort_by(&:last)', [[1, "b"], [2, "a"]].sort_by(&:last).inspect)

# Which to use: a symbol names something fixed in the program, a string holds
# text that came from outside it or that will be built up.
puts
row('symbol as a name', { status: :published }.inspect)
row('string as data', { title: +"Blocks" << " and procs" }.inspect)
