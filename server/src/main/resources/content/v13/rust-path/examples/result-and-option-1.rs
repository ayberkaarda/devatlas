//! Absence as a value, and the ways to get at what is inside without asserting.

#[derive(Debug)]
struct Entry {
    id: &'static str,
    size_bytes: Option<i64>,
}

/// Two reference parameters and a returned reference, so the relationship has
/// to be written out.
fn find<'a>(entries: &'a [Entry], id: &str) -> Option<&'a Entry> {
    entries.iter().find(|entry| entry.id == id)
}

fn main() {
    let entries = [
        Entry { id: "lesson-a", size_bytes: Some(2048) },
        Entry { id: "lesson-b", size_bytes: None },
    ];

    match find(&entries, "lesson-a") {
        Some(entry) => println!("found {} with a known size: {}", entry.id, entry.size_bytes.is_some()),
        None => println!("no such entry"),
    }

    // `and_then` flattens, `map` transforms the inside and leaves absence alone.
    let kib = find(&entries, "lesson-a")
        .and_then(|entry| entry.size_bytes)
        .map(|bytes| bytes / 1024);
    println!("size in KiB: {kib:?}");

    let unknown = find(&entries, "lesson-b")
        .and_then(|entry| entry.size_bytes)
        .map(|bytes| bytes / 1024);
    println!("size of an entry whose size is unknown: {unknown:?}");

    println!(
        "bytes or a default: {}",
        find(&entries, "lesson-c")
            .and_then(|entry| entry.size_bytes)
            .unwrap_or(0)
    );

    // Absence turned into a failure that carries a reason.
    let as_result: Result<i64, &str> = find(&entries, "lesson-c")
        .and_then(|entry| entry.size_bytes)
        .ok_or("ENTITY_NOT_IN_LIBRARY");
    println!("as a Result: {as_result:?}");

    // `if let` when only one case does anything.
    if let Some(entry) = find(&entries, "lesson-b") {
        println!("{} is present but unsized: {}", entry.id, entry.size_bytes.is_none());
    }
}
