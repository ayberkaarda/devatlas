//! Ownership is a scope story: a value is dropped when its owner goes out of
//! scope, and the compiler decides where that is.

struct Tracked {
    name: &'static str,
}

impl Tracked {
    fn new(name: &'static str) -> Self {
        println!("create  {name}");
        Tracked { name }
    }
}

impl Drop for Tracked {
    fn drop(&mut self) {
        println!("drop    {}", self.name);
    }
}

/// Takes ownership. The parameter is the owner now, so the value is dropped
/// when this function returns.
fn consume(item: Tracked) {
    println!("consume {} inside the function", item.name);
}

/// Takes ownership and gives it back. Nothing is dropped here.
fn hand_back(item: Tracked) -> Tracked {
    println!("relay   {}", item.name);
    item
}

fn main() {
    let _outer = Tracked::new("outer");

    {
        let _first = Tracked::new("first");
        let _second = Tracked::new("second");
        println!("-- inner scope ends --");
    }

    let moved = Tracked::new("moved");
    consume(moved);
    println!("consume returned and nothing is left in main to drop");

    let kept = hand_back(Tracked::new("kept"));
    println!("still owned by main: {}", kept.name);

    println!("-- main ends --");
}
