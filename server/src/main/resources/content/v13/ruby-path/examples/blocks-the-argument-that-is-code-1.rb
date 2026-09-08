# A block is a piece of code passed to a method. The method decides when, how
# often, and with what arguments to run it.

def each_word(sentence)
  return "no block given" unless block_given?

  sentence.split.each { |word| yield word }
  sentence.split.size
end

puts each_word("blocks are arguments")
count = each_word("blocks are arguments") { |w| puts "  saw #{w}" }
puts "yielded #{count} times"

# yield hands values to the block and takes its value back.
def twice
  first = yield 1
  second = yield 2
  [first, second]
end

puts "collected -> #{twice { |n| n * 10 }.inspect}"

# A block that is only a method call on its argument has three spellings in
# Ruby 3.4. All three produce the same array.
puts
puts "explicit  -> #{[1, 2, 3].map { |n| n * 2 }.inspect}"
puts "numbered  -> #{[1, 2, 3].map { _1 * 2 }.inspect}"
puts "it        -> #{[1, 2, 3].map { it * 2 }.inspect}"
puts "to_proc   -> #{%w[a b c].map(&:upcase).inspect}"

# A method that yields but is called without a block does not return nil; it
# raises, because there is nothing to yield to.
def demands = yield
begin
  demands
rescue LocalJumpError => e
  puts
  puts "no block  -> #{e.class}: #{e.message}"
end

# Most of the standard library's iterators hand back an Enumerator instead when
# the block is missing, which is why .each without a block is not an error.
puts "each alone -> #{[1, 2].each.class}"
