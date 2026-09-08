//! One annotation, one relationship: which input the returned reference
//! borrows from.

fn longest<'a>(left: &'a str, right: &'a str) -> &'a str {
    if left.len() >= right.len() {
        left
    } else {
        right
    }
}

/// Two independent lifetimes. The result borrows from `haystack` only, so
/// `needle` is free to be a much shorter-lived value.
fn first_containing<'h, 'n>(haystack: &'h [&'h str], needle: &'n str) -> Option<&'h str> {
    haystack.iter().copied().find(|line| line.contains(needle))
}

fn main() {
    let configured = String::from("verifying");
    let fallback = String::from("done");
    println!("longest: {}", longest(&configured, &fallback));

    let states = ["queued", "downloading", "verifying"];
    let found = {
        // `needle` dies at the end of this block. That is allowed because the
        // signature never tied the result to it.
        let needle = String::from("load");
        first_containing(&states, &needle)
    };
    println!("found: {}", found.unwrap_or("nothing"));
    println!(
        "the result outlived the needle: {}",
        found == Some("downloading")
    );
}
