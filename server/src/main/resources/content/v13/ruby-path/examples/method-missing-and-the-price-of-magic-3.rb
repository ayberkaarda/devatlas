# Two failure modes that make method_missing expensive, and one case where it
# is the right tool.

# Failure one: forgetting super. Every unknown name now returns nil, so a typo
# is indistinguishable from a legitimate absent value and nothing ever raises.
class Swallowing
  def initialize(fields) = @fields = fields

  def method_missing(name, *args)
    @fields[name.to_s]
  end
end

s = Swallowing.new({ "title" => "Blocks" })
puts "correct name    -> #{s.title.inspect}"
puts "typo            -> #{s.titel.inspect}"
puts "total nonsense  -> #{s.frobnicate.inspect}"
puts "even with args  -> #{s.save!(1, 2).inspect}"

# Failure two: catching a name the object inherits. method_missing never runs
# for a name that already exists in the chain, so a field called "class" or
# "hash" is unreachable through it whatever the implementation says.
class Shadowed
  def initialize(fields) = @fields = fields

  def method_missing(name, *args)
    return @fields[name.to_s] if @fields.key?(name.to_s)

    super
  end

  def respond_to_missing?(name, priv = false) = @fields.key?(name.to_s) || super
end

sh = Shadowed.new({ "class" => "premium", "frozen?" => "very" })
puts
puts "stored class     -> #{sh.respond_to?(:class)}"
puts "sh.class         -> #{sh.class}"
puts "stored frozen?   -> #{sh.respond_to?(:frozen?)}"
puts "sh.frozen?       -> #{sh.frozen?.inspect}"
puts "inherited names  -> #{(Object.instance_methods & %i[class frozen?]).sort.inspect}"
puts "reachable how    -> #{sh.send(:method_missing, :class).inspect}"

# Where it earns its place: forwarding an open-ended interface to another
# object, where enumerating the names in advance is not possible.
class Audited
  def initialize(target)
    @target = target
    @calls = []
  end

  attr_reader :calls

  def method_missing(name, *args, **kwargs, &block)
    return super unless @target.respond_to?(name)

    @calls << name
    @target.public_send(name, *args, **kwargs, &block)
  end

  def respond_to_missing?(name, priv = false) = @target.respond_to?(name, priv) || super
end

log = Audited.new([3, 1, 2])
puts
puts "sorted          -> #{log.sort.inspect}"
puts "sum             -> #{log.sum}"
puts "first           -> #{log.first}"
puts "calls recorded  -> #{log.calls.inspect}"
puts "respond_to?     -> #{log.respond_to?(:sort)}"

begin
  log.explode
rescue NoMethodError => e
  puts "unknown name    -> #{e.class}: #{e.message}"
end
