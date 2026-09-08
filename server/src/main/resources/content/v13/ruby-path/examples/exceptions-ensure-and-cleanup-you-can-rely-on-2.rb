# Which exceptions a bare rescue catches, and why the hierarchy matters.

# StandardError and everything under it. This is what `rescue => e` means.
begin
  raise ArgumentError, "bad input"
rescue => e
  puts "bare rescue caught -> #{e.class}"
end

# ScriptError, SignalException, SystemExit and NoMemoryError are siblings of
# StandardError under Exception, not children of it, so a bare rescue lets them
# through. That is deliberate: they are not the caller's business.
puts
# grep(Class) drops the modules the interpreter prepends for error reporting,
# leaving the class chain the hierarchy is actually made of.
puts "StandardError chain    -> #{ArgumentError.ancestors.grep(Class).take(4).inspect}"
puts "NotImplementedError    -> #{NotImplementedError.ancestors.grep(Class).take(3).inspect}"
puts "SystemExit             -> #{SystemExit.ancestors.grep(Class).take(2).inspect}"

begin
  begin
    raise NotImplementedError, "no backend"
  rescue => e
    puts "bare rescue caught -> #{e.class}"
  end
rescue Exception => e
  puts "escaped to Exception -> #{e.class}: #{e.message}"
end

# A hierarchy of your own makes a caller able to choose its granularity.
module Store
  class Error < StandardError; end
  class NotFound < Error; end
  class Conflict < Error
    def initialize(key) = super("key #{key} already exists")
  end
end

def lookup(key)
  raise Store::NotFound, "no such key: #{key}"
end

puts
begin
  lookup("missing")
rescue Store::Conflict
  puts "wrong branch"
rescue Store::NotFound => e
  puts "specific branch  -> #{e.class}: #{e.message}"
end

begin
  raise Store::Conflict, "books"
rescue Store::Error => e
  puts "family branch    -> #{e.class}: #{e.message}"
end

# Re-raising inside a rescue records what was being handled at the time, and
# the original is reachable through #cause without being passed explicitly.
def load_config
  Integer("not a number")
rescue ArgumentError
  raise Store::Error, "config is unreadable"
end

puts
begin
  load_config
rescue Store::Error => e
  puts "raised           -> #{e.class}: #{e.message}"
  puts "cause            -> #{e.cause.class}: #{e.cause.message}"
  puts "cause of cause   -> #{e.cause.cause.inspect}"
end

# rescue can name several classes, and the order of the clauses is the order
# they are tried, so the general one must come last.
[ArgumentError, TypeError, Store::NotFound].each do |klass|
  begin
    raise klass, "example"
  rescue ArgumentError, TypeError => e
    puts "grouped clause   -> #{e.class}"
  rescue StandardError => e
    puts "fallback clause  -> #{e.class}"
  end
end
