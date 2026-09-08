# The mock that tested itself: a test that passes because the double was told
# what to say, not because the code under test is right.

require "minitest"
require "minitest/mock"

# The collaborator. It applies a percentage discount.
class Pricing
  def discounted(cents, percent) = cents - (cents * percent / 100)
end

# The code under test. The bug is deliberate: percent and cents are the wrong
# way round in the call.
class Checkout
  def initialize(pricing) = @pricing = pricing

  def total(cents, percent) = @pricing.discounted(percent, cents)
end

class SelfTestingMockTest < Minitest::Test
  # This passes, and proves nothing. The mock is told to accept any two
  # arguments and to answer 900, and 900 is exactly what the test asserts.
  def test_with_a_mock_that_answers_the_question
    pricing = Minitest::Mock.new
    pricing.expect(:discounted, 900) { |_a, _b| true }

    assert_equal 900, Checkout.new(pricing).total(1000, 10)
    pricing.verify
  end

  # The same mock, now told which arguments it should receive. The argument
  # order bug is caught, because the expectation describes the call and not
  # only the answer.
  def test_with_a_mock_that_states_the_call
    pricing = Minitest::Mock.new
    pricing.expect(:discounted, 900, [1000, 10])

    Checkout.new(pricing).total(1000, 10)
    pricing.verify
  end

  # The real collaborator catches it without any expectation at all.
  def test_against_the_real_collaborator
    assert_equal 900, Checkout.new(Pricing.new).total(1000, 10)
  end
end

%i[
  test_with_a_mock_that_answers_the_question
  test_with_a_mock_that_states_the_call
  test_against_the_real_collaborator
].each do |name|
  result = SelfTestingMockTest.new(name).run
  puts format("%-46s %s", name, result.passed? ? "pass" : "FAIL")
  result.failures.each do |failure|
    # An UnexpectedError wraps the real exception, and its message carries a
    # backtrace. Only the exception itself is reported here, so that the output
    # does not depend on where the file happens to live.
    detail =
      if failure.is_a?(Minitest::UnexpectedError)
        "#{failure.error.class}: #{failure.error.message.lines.first.strip}"
      else
        failure.message.lines.map(&:strip).reject(&:empty?).join(" | ")
      end
    puts "    #{detail}"
  end
end
