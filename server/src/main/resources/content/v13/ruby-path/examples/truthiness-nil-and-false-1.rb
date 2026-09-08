# Ruby has exactly two false values. Everything else is true in a condition,
# including the values other languages treat as false.

candidates = [
  nil, false, true,
  0, 0.0, -1, Float::NAN,
  "", " ", "false",
  [], {}, :sym
]

candidates.each do |value|
  verdict = value ? "truthy" : "falsy"
  printf("%-12s %-10s %s\n", value.inspect, value.class, verdict)
end

puts
puts "falsy values -> #{candidates.reject { it }.inspect}"
puts "count falsy  -> #{candidates.count { |v| !v }}"

# The two are not equal to each other, and neither is equal to zero or "".
puts
puts "nil == false -> #{(nil == false).inspect}"
puts "nil.nil?     -> #{nil.nil?}"
puts "false.nil?   -> #{false.nil?}"
puts "0 == false   -> #{(0 == false).inspect}"

# && and || return one of their operands, not a boolean. That is what makes
# them useful for defaulting and what makes them dangerous with false.
puts
puts "nil || 8080    -> #{(nil || 8080).inspect}"
puts "0 || 8080      -> #{(0 || 8080).inspect}"
puts "false || 8080  -> #{(false || 8080).inspect}"
puts "1 && nil       -> #{(1 && nil).inspect}"
puts "1 && 2         -> #{(1 && 2).inspect}"

# To get an actual boolean, ask for one.
puts
puts "!!nil          -> #{(!!nil).inspect}"
puts "!!0            -> #{(!!0).inspect}"
