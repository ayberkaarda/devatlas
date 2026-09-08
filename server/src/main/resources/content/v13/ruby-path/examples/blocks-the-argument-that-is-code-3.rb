# The pattern blocks exist for: a method that owns a resource, lends it to the
# caller for the duration of a block, and takes it back whatever happens.

class Ledger
  def self.open(name)
    ledger = new(name)
    ledger.log("opened")
    return ledger unless block_given?

    begin
      yield ledger
    ensure
      ledger.log("closed")
      ledger.close
    end
  end

  attr_reader :entries

  def initialize(name)
    @name = name
    @entries = []
    @open = true
  end

  def log(event)
    raise IOError, "ledger #{@name} is closed" unless @open

    @entries << event
    self
  end

  def close = @open = false
  def open? = @open
end

# Normal path: the block runs, then the ensure clause closes the ledger.
result = Ledger.open("books") do |ledger|
  ledger.log("credit 250")
  ledger.log("debit 100")
  ledger.entries.size
end
puts "block value    -> #{result}"

# Failure path: the block raises, the ledger is still closed, and the exception
# still reaches the caller.
ledger_seen = nil
begin
  Ledger.open("books") do |ledger|
    ledger_seen = ledger
    ledger.log("credit 10")
    raise ArgumentError, "bad entry"
  end
rescue ArgumentError => e
  puts "raised         -> #{e.class}: #{e.message}"
end
puts "closed anyway  -> #{!ledger_seen.open?}"
puts "entries kept   -> #{ledger_seen.entries.inspect}"

# Without a block the method hands the resource back and the caller owns it.
manual = Ledger.open("manual")
puts "no block open? -> #{manual.open?}"
manual.close
begin
  manual.log("late")
rescue IOError => e
  puts "after close    -> #{e.class}: #{e.message}"
end
