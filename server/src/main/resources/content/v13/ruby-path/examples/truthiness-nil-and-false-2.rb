# The bug: || and ||= cannot tell "absent" from "present and false".

settings = { retries: 0, verbose: false }

# Both defaults are wrong, and neither raises.
retries = settings[:retries] || 3
verbose = settings[:verbose] || true

puts "stored retries  -> #{settings[:retries].inspect}"
puts "|| gave         -> #{retries.inspect}"
puts "stored verbose  -> #{settings[:verbose].inspect}"
puts "|| gave         -> #{verbose.inspect}"

# 0 survives because 0 is truthy in Ruby; false does not.
puts "retries correct -> #{retries == settings[:retries]}"
puts "verbose correct -> #{verbose == settings[:verbose]}"

# fetch distinguishes the two cases, because it asks about the key rather than
# about the value's truthiness.
puts
puts "fetch present   -> #{settings.fetch(:verbose, true).inspect}"
puts "fetch absent    -> #{settings.fetch(:colour, "auto").inspect}"
puts "key? verbose    -> #{settings.key?(:verbose)}"
puts "key? colour     -> #{settings.key?(:colour)}"

# Without a default, fetch raises rather than handing back a nil that travels.
begin
  settings.fetch(:colour)
rescue KeyError => e
  puts "fetch no default -> #{e.class}: #{e.message}"
end

# ||= has the same blind spot, and it bites hardest on a memoised boolean: the
# expensive call is repeated on every access because false never sticks.
class Probe
  attr_reader :calls

  def initialize = @calls = 0

  def reachable?
    @reachable ||= begin
      @calls += 1
      false
    end
  end

  def reachable_fixed?
    return @fixed if defined?(@fixed)

    @calls += 1
    @fixed = false
  end
end

p1 = Probe.new
3.times { p1.reachable? }
puts
puts "||= memoised    -> value #{p1.reachable?.inspect}, computed #{p1.calls} times"

p2 = Probe.new
3.times { p2.reachable_fixed? }
puts "defined? guard  -> value #{p2.reachable_fixed?.inspect}, computed #{p2.calls} times"
