//! The same computation twice: a hand-written loop and an adapter chain.

#[derive(Debug, Clone)]
struct Entry {
    id: &'static str,
    size_bytes: i64,
    done: bool,
}

fn total_by_loop(entries: &[Entry]) -> i64 {
    let mut total = 0;
    for entry in entries {
        if !entry.done {
            continue;
        }
        total += entry.size_bytes;
    }
    total
}

fn total_by_chain(entries: &[Entry]) -> i64 {
    entries
        .iter()
        .filter(|entry| entry.done)
        .map(|entry| entry.size_bytes)
        .sum()
}

fn main() {
    let entries = [
        Entry { id: "lesson-a", size_bytes: 2048, done: true },
        Entry { id: "lesson-b", size_bytes: 640, done: false },
        Entry { id: "lesson-c", size_bytes: 4096, done: true },
        Entry { id: "mind-map", size_bytes: 128, done: true },
    ];

    println!("loop:  {}", total_by_loop(&entries));
    println!("chain: {}", total_by_chain(&entries));
    println!(
        "the two agree: {}",
        total_by_loop(&entries) == total_by_chain(&entries)
    );

    // Adapters are lazy. Nothing is examined until a consumer pulls, and a
    // consumer that can stop early does.
    let mut examined = 0;
    let first_large = entries
        .iter()
        .inspect(|_| examined += 1)
        .find(|entry| entry.size_bytes > 1000);
    println!("first large entry: {}", first_large.map(|e| e.id).unwrap_or("none"));
    println!("entries examined before stopping: {examined}");
    println!("fewer than the whole slice: {}", examined < entries.len());

    // A chain with no consumer does nothing at all, which is why `Iterator` is
    // marked as a value that must be used.
    let counted = entries.iter().filter(|entry| entry.done).count();
    println!("entries already done: {counted}");
}
