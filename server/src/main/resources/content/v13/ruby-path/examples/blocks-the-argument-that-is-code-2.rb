# Capturing a block turns it into an object you can store, pass on and inspect.

def capture(&block)
  puts "captured class -> #{block.class}"
  puts "arity          -> #{block.arity}"
  puts "lambda?        -> #{block.lambda?}"
  block.call(5)
end

puts "result         -> #{capture { |n| n + 1 }}"

# Passing it on costs one & in each direction.
def outer(&block) = inner(&block)
def inner = yield("relayed")
puts "relayed        -> #{outer { |s| s.upcase }}"

# Procs and lambdas are both Proc instances and behave differently in two ways
# that matter. Arity first: a proc pads and drops, a lambda refuses.
pr = proc { |a, b| [a, b].inspect }
la = lambda { |a, b| [a, b].inspect }

puts
puts "proc   too few  -> #{pr.call(1)}"
puts "proc   too many -> #{pr.call(1, 2, 3)}"
begin
  la.call(1)
rescue ArgumentError => e
  puts "lambda too few  -> #{e.class}: #{e.message}"
end

# Return second: a return inside a proc returns from the enclosing method; a
# return inside a lambda returns from the lambda only.
def with_proc
  proc { return :left_early }.call
  :reached_the_end
end

def with_lambda
  lambda { return :inner_value }.call
  :reached_the_end
end

puts
puts "proc return    -> #{with_proc.inspect}"
puts "lambda return  -> #{with_lambda.inspect}"

# A block captured with & becomes a non-lambda proc, so a bare return inside a
# block behaves like the proc case above rather than the lambda one.
def flag_of_block(&block) = block.lambda?
puts "block is lambda? -> #{flag_of_block {}}"
puts "lambda is lambda? -> #{la.lambda?}"
