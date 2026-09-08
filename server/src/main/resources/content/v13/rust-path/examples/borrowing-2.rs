//! An exclusive reference, and where its region actually ends.

fn bump(counter: &mut i64) {
    *counter += 1;
}

fn append(log: &mut Vec<String>, line: &str) {
    log.push(line.to_string());
}

fn main() {
    let mut attempts = 0i64;
    bump(&mut attempts);
    bump(&mut attempts);
    // Each call created an exclusive reference and gave it back on return.
    println!("attempts: {attempts}");

    let mut log: Vec<String> = Vec::new();
    let handle = &mut log;
    append(handle, "queued");
    append(handle, "downloading");
    // `handle` is never used again, so its region has ended and `log` may be
    // borrowed again -- no block was needed to release it.
    let view = &log;
    println!("entries: {}", view.len());
    println!("last entry: {}", view.last().map(String::as_str).unwrap_or(""));

    let mut name = String::from("queue");
    let exclusive = &mut name;
    exclusive.push_str("-entry");
    // The exclusive borrow above is dead from here, so a shared one is legal
    // even though both are still in scope by the braces.
    let shared = &name;
    println!("name: {shared}");
}
