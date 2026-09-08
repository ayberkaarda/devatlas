//! The shape of a test: one function under test, a table of cases, and a body
//! that reports rather than unwraps.

fn is_newer(local: i64, remote: i64) -> bool {
    remote > local
}

/// A test body may return `Result`, which lets it use `?` on a fallible step
/// instead of unwrapping and losing the reason.
fn parses_a_version() -> Result<(), String> {
    let parsed: i64 = "13".parse().map_err(|_| String::from("not a number"))?;
    if parsed != 13 {
        return Err(format!("expected 13, got {parsed}"));
    }
    Ok(())
}

fn rejects_a_word() -> Result<(), String> {
    let parsed: i64 = "thirteen".parse().map_err(|_| String::from("not a number"))?;
    Err(format!("a word should not have parsed, but gave {parsed}"))
}

fn main() {
    let cases: [(i64, i64, bool); 5] = [
        (1, 2, true),
        (2, 2, false),
        (3, 2, false),
        (0, 1, true),
        (13, 13, false),
    ];

    let mut failures = 0;
    for (local, remote, expected) in cases {
        let actual = is_newer(local, remote);
        if actual != expected {
            failures += 1;
            println!("case ({local}, {remote}): expected {expected}, got {actual}");
        }
    }
    println!("cases run: {}", cases.len());
    println!("cases failed: {failures}");

    // A table makes the failing case name itself. A single assertion over a
    // loop would only say that something, somewhere, disagreed.
    println!("parses_a_version: {:?}", parses_a_version());
    println!("rejects_a_word:   {:?}", rejects_a_word());
    println!(
        "the second returned its reason rather than panicking: {}",
        rejects_a_word().is_err()
    );
}
