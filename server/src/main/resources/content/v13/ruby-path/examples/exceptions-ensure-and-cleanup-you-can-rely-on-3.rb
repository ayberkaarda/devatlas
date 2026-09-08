# Cleanup you can rely on: a resource that is always released, a retry that is
# bounded, and a failure that is reported rather than hidden.

class FlakyConnection
  class Refused < StandardError; end

  attr_reader :attempts, :open_count, :close_count

  def initialize(fail_times)
    @fail_times = fail_times
    @attempts = 0
    @open_count = 0
    @close_count = 0
  end

  def open
    @attempts += 1
    raise Refused, "connection refused (attempt #{@attempts})" if @attempts <= @fail_times

    @open_count += 1
    self
  end

  def close = @close_count += 1
  def read = "payload"
end

MAX_ATTEMPTS = 3

def with_connection(conn)
  tries = 0
  begin
    tries += 1
    conn.open
  rescue FlakyConnection::Refused => e
    if tries < MAX_ATTEMPTS
      puts "  retrying after: #{e.message}"
      retry
    end
    raise
  end

  begin
    yield conn
  ensure
    conn.close
  end
end

# Succeeds on the third attempt; the connection is opened once and closed once.
flaky = FlakyConnection.new(2)
value = with_connection(flaky) { |c| c.read }
puts "value          -> #{value.inspect}"
puts "attempts       -> #{flaky.attempts}"
puts "opened/closed  -> #{flaky.open_count}/#{flaky.close_count}"

# The block raises: the connection is still closed, and the caller still sees
# the block's exception rather than a cleanup error.
puts
broken = FlakyConnection.new(0)
begin
  with_connection(broken) { raise KeyError, "no such record" }
rescue KeyError => e
  puts "caller saw     -> #{e.class}: #{e.message}"
end
puts "opened/closed  -> #{broken.open_count}/#{broken.close_count}"

# Retries run out: the last exception is re-raised, unchanged, and nothing was
# opened so nothing needs closing.
puts
hopeless = FlakyConnection.new(10)
begin
  with_connection(hopeless) { |c| c.read }
rescue FlakyConnection::Refused => e
  puts "gave up after  -> #{hopeless.attempts} attempts"
  puts "final          -> #{e.class}: #{e.message}"
end
puts "opened/closed  -> #{hopeless.open_count}/#{hopeless.close_count}"

# An exception raised inside ensure replaces the one that was travelling, so
# cleanup that can fail must contain its own failure.
def careless
  raise IOError, "the real problem"
ensure
  raise ArgumentError, "the cleanup problem"
end

puts
begin
  careless
rescue => e
  puts "caller saw     -> #{e.class}: #{e.message}"
  puts "original       -> #{e.cause.class}: #{e.cause.message}"
end
