//! Shared behaviour declared separately from the types that provide it.

trait Describe {
    /// Required: every implementor writes this one.
    fn code(&self) -> &'static str;

    /// Provided: written once here, and inherited by any implementor that does
    /// not replace it.
    fn describe(&self) -> String {
        format!("<{}>", self.code())
    }
}

struct Lesson {
    slug: String,
}

struct MindMap;

impl Describe for Lesson {
    fn code(&self) -> &'static str {
        "LESSON"
    }

    fn describe(&self) -> String {
        format!("lesson {}", self.slug)
    }
}

impl Describe for MindMap {
    fn code(&self) -> &'static str {
        "MIND_MAP"
    }
}

/// A bound, not a base class. One copy of this function is compiled for each
/// concrete `T` the program actually calls it with.
fn report<T: Describe>(item: &T) -> String {
    format!("{} :: {}", item.code(), item.describe())
}

/// The same bound written shorter. `impl Trait` in argument position is
/// exactly the generic above, without a name for the parameter.
fn code_of(item: &impl Describe) -> &'static str {
    item.code()
}

fn main() {
    let lesson = Lesson {
        slug: String::from("ownership"),
    };
    let map = MindMap;

    println!("{}", report(&lesson));
    println!("{}", report(&map));
    println!("code of the lesson: {}", code_of(&lesson));
    println!("code of the map: {}", code_of(&map));
    println!(
        "the default was used only where it was not replaced: {}",
        map.describe() == "<MIND_MAP>" && lesson.describe() == "lesson ownership"
    );
}
