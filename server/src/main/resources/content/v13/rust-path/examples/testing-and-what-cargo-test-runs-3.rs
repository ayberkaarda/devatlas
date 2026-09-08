//! What a test build contains that an ordinary build does not.

/// Compiled only when the crate is built for testing. In a normal build this
/// module does not exist at all, so its imports and helpers cost nothing.
#[cfg(test)]
mod tests {
    use super::is_terminal;

    #[test]
    fn queued_is_not_terminal() {
        assert!(!is_terminal("QUEUED"));
    }

    #[test]
    fn done_and_failed_are_terminal() {
        assert!(is_terminal("DONE"));
        assert!(is_terminal("FAILED"));
    }
}

fn is_terminal(state: &str) -> bool {
    matches!(state, "DONE" | "FAILED")
}

fn main() {
    // `cfg!` is the expression form of the same condition the attribute uses,
    // so this binary can report which of the two builds it is.
    println!("compiled with cfg(test): {}", cfg!(test));
    println!("the module above is in this binary: {}", cfg!(test));

    // `assert!` runs in every build. `debug_assert!` is compiled out when
    // debug assertions are off, so it may state something expensive.
    assert!(is_terminal("FAILED"), "FAILED must be terminal");
    debug_assert!(!is_terminal("QUEUED"), "QUEUED must not be terminal");
    println!("both assertions held: {}", is_terminal("DONE") && !is_terminal("QUEUED"));

    println!("terminal states: {} of 4", ["QUEUED", "DOWNLOADING", "DONE", "FAILED"]
        .iter()
        .filter(|state| is_terminal(state))
        .count());
}
