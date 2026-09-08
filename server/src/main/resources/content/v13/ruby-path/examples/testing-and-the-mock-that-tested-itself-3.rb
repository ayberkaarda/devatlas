# What a double is allowed to check, and what only the real object can.

require "minitest"
require "minitest/mock"

def row(label, value) = puts(format("%-32s -> %s", label, value))

# verify is the half of Minitest::Mock that people forget. Without it, an
# expectation that was never met costs nothing.
unmet = Minitest::Mock.new
unmet.expect(:save, true)
row("expectation set, never called", "no error yet")
begin
  unmet.verify
rescue MockExpectationError => e
  row("verify", "#{e.class}: #{e.message}")
end

# A mock refuses a message it was not told about, and the refusal is a
# NoMethodError rather than a mock-specific error.
strict = Minitest::Mock.new
begin
  strict.anything_at_all
rescue NoMethodError => e
  row("unmocked call", "#{e.class}: #{e.message}")
end

# Arguments can be matched by value, by class, or by a block that decides.
puts
by_value = Minitest::Mock.new
by_value.expect(:charge, :ok, [1000, "GBP"])
row("matched by value", by_value.charge(1000, "GBP"))
row("verify", by_value.verify)

by_class = Minitest::Mock.new
by_class.expect(:charge, :ok, [Integer, String])
row("matched by class", by_class.charge(250, "EUR"))

by_block = Minitest::Mock.new
by_block.expect(:charge, :ok) { |amount, currency| amount.positive? && currency.size == 3 }
row("matched by block", by_block.charge(250, "EUR"))

rejecting = Minitest::Mock.new
rejecting.expect(:charge, :ok) { |amount, _| amount.positive? }
begin
  rejecting.charge(-1, "EUR")
rescue MockExpectationError => e
  row("block rejects", "#{e.class}: #{e.message}")
end

# stub replaces one method on a real object for the duration of a block, so
# the rest of the object stays real. This is the smaller, safer tool.
class Clock
  def self.today = "the real date"
  def self.zone = "the real zone"
end

puts
Clock.stub(:today, "2026-01-01") do
  row("stubbed method", Clock.today)
  row("untouched method", Clock.zone)
end
row("after the block", Clock.today)

# A double cannot notice that the real object's interface changed. This one
# still answers a method the real class no longer has, so a test built on it
# keeps passing after the code it describes has gone.
class Pricing
  def discount_for(_customer) = 10
end

double = Minitest::Mock.new
double.expect(:discount, 10, [Object])
puts
row("real class responds to", Pricing.instance_methods(false).sort.inspect)
row("double answers", double.discount(Object.new))
row("real object would not", Pricing.new.respond_to?(:discount))
