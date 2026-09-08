# Minitest ships with Ruby, so a test file needs no project setup. Requiring
# "minitest/autorun" is the usual way to run one; here each test is run
# explicitly instead, so that what is printed is the result and not the
# framework's own summary line, which carries a duration that changes on
# every run.

require "minitest"

class Basket
  def initialize = @items = {}

  def add(name, qty)
    raise ArgumentError, "qty must be positive" unless qty.positive?

    @items[name] = @items.fetch(name, 0) + qty
    self
  end

  def count(name) = @items.fetch(name, 0)
  def total = @items.values.sum
  def names = @items.keys
end

class BasketTest < Minitest::Test
  def setup = @basket = Basket.new

  def test_adds_an_item
    @basket.add("apple", 2)
    assert_equal 2, @basket.count("apple")
  end

  def test_accumulates_the_same_item
    @basket.add("apple", 2).add("apple", 3)
    assert_equal 5, @basket.count("apple")
    assert_equal 5, @basket.total
  end

  def test_keeps_insertion_order
    @basket.add("pear", 1).add("apple", 1)
    assert_equal %w[pear apple], @basket.names
  end

  def test_rejects_a_non_positive_quantity
    error = assert_raises(ArgumentError) { @basket.add("apple", 0) }
    assert_equal "qty must be positive", error.message
  end

  # Written wrong on purpose, to show what a failure reports.
  def test_that_fails
    @basket.add("apple", 2)
    assert_equal 3, @basket.count("apple")
  end
end

# Minitest deliberately randomises the order its own runner uses. Sorting the
# names here keeps this listing's output the same on every run.
BasketTest.public_instance_methods(false).grep(/\Atest_/).sort.each do |name|
  result = BasketTest.new(name).run
  status = result.passed? ? "pass" : "FAIL"
  puts format("%-38s %s  assertions=%d", name, status, result.assertions)
  result.failures.each do |failure|
    # Only the assertion's own message is printed. Minitest attaches a
    # backtrace, and a backtrace names the directory the file was run from.
    detail = failure.message.lines.map(&:strip).reject(&:empty?).join(" | ")
    puts "    #{failure.class}: #{detail}"
  end
end
