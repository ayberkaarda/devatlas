# Building an answer out of a collection: three shapes, and when each fits.

Entry = Struct.new(:account, :amount, :kind) do
  def to_s = "#{account} #{kind} #{amount}"
end

entries = [
  Entry.new("books", 250, :credit),
  Entry.new("rent", 900, :debit),
  Entry.new("books", 120, :debit),
  Entry.new("food", 300, :debit)
]

# reduce carries one accumulator and returns it.
total = entries.reduce(0) { |sum, e| e.kind == :credit ? sum + e.amount : sum - e.amount }
puts "reduce total      -> #{total}"
puts "sum shorthand     -> #{entries.sum(&:amount)}"

# each_with_object carries a mutable accumulator and returns it without the
# block having to hand it back on every iteration.
by_account = entries.each_with_object(Hash.new(0)) { |e, h| h[e.account] += e.amount }
puts "each_with_object  -> #{by_account.inspect}"

# group_by and partition split rather than fold.
puts "group_by kind     -> #{entries.group_by(&:kind).transform_values(&:size).inspect}"
debits, credits = entries.partition { it.kind == :debit }
puts "partition sizes   -> debits #{debits.size}, credits #{credits.size}"

# The results above are Hashes, and a Hash presents its entries in creation
# order, so the order printed is the order the entries were visited. That is a
# guarantee of the language, not an accident of this run.
puts
puts "visit order       -> #{entries.map(&:account).inspect}"
puts "hash key order    -> #{by_account.keys.inspect}"

# filter_map and flat_map avoid the intermediate array that map+compact and
# map+flatten would build.
puts
puts "filter_map        -> #{entries.filter_map { it.account if it.amount > 200 }.inspect}"
puts "flat_map          -> #{entries.flat_map { [it.account, it.kind] }.first(4).inspect}"
puts "tally             -> #{entries.map(&:account).tally.inspect}"
puts "min_by / max_by   -> #{entries.min_by(&:amount).to_s} / #{entries.max_by(&:amount)}"
puts "sum by group      -> #{entries.group_by(&:account).transform_values { |es| es.sum(&:amount) }.inspect}"

# A Hash is itself Enumerable, and it yields two-element pairs.
puts
puts "hash map          -> #{by_account.map { |k, v| "#{k}=#{v}" }.inspect}"
puts "hash select       -> #{by_account.select { |_, v| v > 300 }.inspect}"
puts "hash sum          -> #{by_account.sum { |_, v| v }}"
puts "hash to_a         -> #{by_account.to_a.inspect}"
