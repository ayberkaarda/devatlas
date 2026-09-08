# Three tools that read better than a bare truth test, and what each one means.

record = { name: "Ada", nickname: nil, admin: false }

# 1. nil? asks about absence. A falsy test conflates it with false.
puts "nickname nil?   -> #{record[:nickname].nil?}"
puts "admin nil?      -> #{record[:admin].nil?}"
puts "nickname falsy  -> #{!record[:nickname]}"
puts "admin falsy     -> #{!record[:admin]}"

# 2. Safe navigation calls the method only when the receiver is not nil, and
# evaluates to nil otherwise. It does not suppress other errors.
puts
puts "name&.upcase     -> #{record[:name]&.upcase.inspect}"
puts "nickname&.upcase -> #{record[:nickname]&.upcase.inspect}"
begin
  record[:admin]&.upcase
rescue NoMethodError => e
  puts "admin&.upcase    -> #{e.class}: #{e.message}"
end

# 3. Conditional assignment and the empty-string trap. "" is truthy, so a blank
# form field passes a bare check and fails a meaningful one.
submitted = ""
puts
puts "submitted truthy -> #{submitted ? true : false}"
puts "empty?           -> #{submitted.empty?}"
display = submitted.empty? ? "anonymous" : submitted
puts "chosen           -> #{display.inspect}"

# A common idiom that trips people: a method ending in ? is expected to return
# a boolean, but nothing enforces that. This one returns nil or a String.
def role_for(user)
  user[:admin] ? "admin" : nil
end

puts
puts "role_for         -> #{role_for(record).inspect}"
puts "used as boolean  -> #{role_for(record) ? "yes" : "no"}"

# if and unless are expressions: they evaluate to a value, and an if with no
# matching branch evaluates to nil rather than to nothing.
value = if record[:admin]
          "elevated"
        end
puts
puts "if with no else  -> #{value.inspect}"
puts "unless           -> #{(record[:nickname] ? "has one" : "none")}"

# Comparison against nil is safe on every object because every object answers
# to == and nil is an object like any other.
puts "nil == nil       -> #{(nil == nil).inspect}"
puts "\"\" == nil        -> #{("" == nil).inspect}"
