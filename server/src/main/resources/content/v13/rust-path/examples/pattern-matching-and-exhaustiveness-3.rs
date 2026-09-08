//! Two pattern rules that Rust 2024 changed, both observable here.

use std::cell::RefCell;

struct Manifest {
    etag: Option<String>,
    version: Option<i64>,
}

fn main() {
    let manifest = Manifest {
        etag: Some(String::from("w/\"7\"")),
        version: Some(13),
    };

    // A let chain: several `let` patterns and ordinary conditions joined by
    // `&&` in one `if`. Rust 2024 accepts this; earlier editions reject it.
    if let Some(etag) = &manifest.etag
        && let Some(version) = manifest.version
        && version > 0
    {
        println!("conditional request possible for version {version}");
        println!("etag is {} characters", etag.chars().count());
    }

    let incomplete = Manifest { etag: None, version: Some(13) };
    if let Some(_etag) = &incomplete.etag
        && let Some(_version) = incomplete.version
    {
        println!("not reached for this value");
    } else {
        println!("the chain stopped at the first pattern that did not match");
    }

    // In Rust 2024 the temporary built for the scrutinee is dropped before the
    // `else` block runs, so the shared borrow taken to test the value is over
    // by the time the block asks for an exclusive one.
    let slot: RefCell<Option<i32>> = RefCell::new(None);
    if let Some(value) = *slot.borrow() {
        println!("already held {value}");
    } else {
        *slot.borrow_mut() = Some(9);
        println!("filled in the else branch");
    }
    println!("the slot now holds a value: {}", slot.borrow().is_some());
}
