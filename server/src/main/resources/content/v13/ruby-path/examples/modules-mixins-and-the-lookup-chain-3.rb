# What a module can and cannot reach, and the hook that adds class methods.

module Trackable
  # Runs when a class includes this module. base is that class.
  def self.included(base)
    base.extend(ClassMethods)
    base.instance_variable_set(:@tracked_fields, [])
  end

  module ClassMethods
    def track(field)
      @tracked_fields << field
      self
    end

    def tracked_fields = @tracked_fields
  end

  # Instance methods. They call methods the including class is expected to
  # provide; the module does not know how they are implemented.
  def changes
    self.class.tracked_fields.to_h { |f| [f, public_send(f)] }
  end
end

class Article
  include Trackable

  track :title
  track :status

  attr_accessor :title, :status

  def initialize(title, status)
    @title = title
    @status = status
  end
end

article = Article.new("Blocks", "draft")
puts "class methods   -> #{Article.tracked_fields.inspect}"
puts "instance method -> #{article.changes.inspect}"
article.status = "published"
puts "after change    -> #{article.changes.inspect}"

# Two classes including the same module get separate state, because the hook
# ran once per class.
class Comment
  include Trackable
  track :body
  attr_accessor :body
  def initialize(body) = @body = body
end

puts
puts "Article fields  -> #{Article.tracked_fields.inspect}"
puts "Comment fields  -> #{Comment.tracked_fields.inspect}"
puts "Comment changes -> #{Comment.new("first").changes.inspect}"

# The chain is one list per class, and a module appears in it once no matter
# how many times it is included.
class Article
  include Trackable
  include Trackable
end
puts
puts "Trackable count -> #{Article.ancestors.count(Trackable)}"
puts "Article chain   -> #{Article.ancestors.take(4).inspect}"

# A module method called on an object that lacks the expected method fails at
# the call, not at the include. Mixins are not a contract the loader checks.
class Empty
  include Trackable
  track :missing
end

begin
  Empty.new.changes
rescue NoMethodError => e
  puts
  puts "missing member  -> #{e.class}: #{e.message}"
end
