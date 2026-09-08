# The four clauses and the order they run in. A method body is an implicit
# begin, so the clauses can be written straight into it.

def parse(text)
  puts "  body"
  Integer(text)
rescue ArgumentError => e
  puts "  rescue: #{e.class}"
  -1
else
  puts "  else"
  :parsed_cleanly
ensure
  puts "  ensure"
end

puts "parse(\"42\"):"
puts "-> #{parse("42").inspect}"
puts
puts "parse(\"abc\"):"
puts "-> #{parse("abc").inspect}"

# Note what the successful call returned. When an else clause is present and no
# exception was raised, its value is the value of the expression, so the body's
# own result is discarded. That is a real source of surprise.
puts
puts "body computed Integer(\"42\") -> #{Integer("42")}"
puts "but the method returned     -> #{parse("42").inspect}"

# ensure runs while an exception is unwinding too, and does not stop it.
def unwinding
  raise IOError, "disk gone"
ensure
  puts "  ensure ran while unwinding"
end

puts
begin
  unwinding
rescue IOError => e
  puts "caller still saw -> #{e.class}: #{e.message}"
end

# ensure also runs before a return value leaves the method.
def early
  return :from_body
ensure
  puts "  ensure before the value leaves"
end

puts
puts "early -> #{early.inspect}"

# And an explicit return inside ensure swallows the exception entirely, which
# is almost never what was meant.
def swallowing
  raise IOError, "disk gone"
ensure
  return :nothing_happened
end

puts
puts "swallowing -> #{swallowing.inspect}"
puts "the IOError never reached the caller"
