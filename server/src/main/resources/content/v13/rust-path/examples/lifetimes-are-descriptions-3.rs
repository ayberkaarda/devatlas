//! The annotation extends nothing. It states which input the result borrows
//! from, and the caller has to satisfy that.

/// The result borrows from `primary` alone; `fallback` is unrelated to it.
fn prefer_primary<'p>(primary: &'p str, _fallback: &str) -> &'p str {
    primary
}

/// The result may come from either argument, so both have to outlive it.
fn either<'a>(left: &'a str, right: &'a str) -> &'a str {
    if left.len() >= right.len() {
        left
    } else {
        right
    }
}

const BUILT_IN: &str = "built-in";

fn main() {
    let primary = String::from("configured");

    let chosen = {
        let fallback = String::from("temporary");
        // Legal: the signature ties the result to `primary`, so the short life
        // of `fallback` is irrelevant.
        prefer_primary(&primary, &fallback)
    };
    println!("chosen: {chosen}");

    // `'static` is not a privilege, only the longest possible region, and a
    // longer one is accepted wherever a shorter one is asked for.
    println!("either, given a 'static argument: {}", either(&primary, BUILT_IN));

    let shorter = String::from("short");
    let picked = either(&primary, &shorter);
    println!("both arguments outlive the result: {}", picked == primary);
}
