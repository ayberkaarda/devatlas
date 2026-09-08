//! What an assertion failure actually reports, observed without the harness.

use std::panic;

/// Captures the panic message a piece of code produces, or `None` if it did
/// not panic. This is the mechanism a test harness uses to survive a failing
/// test and carry on to the next one.
fn failure_message(body: impl FnOnce() + panic::UnwindSafe) -> Option<String> {
    let previous = panic::take_hook();
    panic::set_hook(Box::new(|_| {}));
    let outcome = panic::catch_unwind(body);
    panic::set_hook(previous);

    outcome.err().map(|payload| {
        if let Some(text) = payload.downcast_ref::<&str>() {
            (*text).to_string()
        } else if let Some(text) = payload.downcast_ref::<String>() {
            text.clone()
        } else {
            String::from("a payload that is not a string")
        }
    })
}

fn main() {
    let message = failure_message(|| assert_eq!(2 + 2, 5)).expect("this assertion fails");
    println!(
        "assert_eq names both sides: {}",
        message.contains("left") && message.contains("right")
    );
    println!(
        "assert_eq prints the two values: {}",
        message.contains('4') && message.contains('5')
    );

    let message = failure_message(|| {
        assert!(13 < 12, "version {} is not newer than {}", 13, 12);
    })
    .expect("this assertion fails");
    println!("a custom message replaces the default: {message}");

    println!(
        "a passing assertion produces nothing: {}",
        failure_message(|| assert_eq!(2 + 2, 4)).is_none()
    );

    // This is what `#[should_panic]` checks, and why it takes an `expected`
    // fragment: without one, any panic at all counts as success.
    let message = failure_message(|| panic!("DIGEST_MISMATCH while verifying"))
        .expect("this panics on purpose");
    println!(
        "the panic was the expected one: {}",
        message.contains("DIGEST_MISMATCH")
    );
}
