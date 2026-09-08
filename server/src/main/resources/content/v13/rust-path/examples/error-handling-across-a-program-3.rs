//! Two audiences in one error: a code a program branches on, and a message a
//! person reads.

use std::fmt;

mod codes {
    pub const DIGEST_MISMATCH: &str = "DIGEST_MISMATCH";
    pub const NETWORK_UNAVAILABLE: &str = "NETWORK_UNAVAILABLE";
    pub const ENTITY_NOT_IN_LIBRARY: &str = "ENTITY_NOT_IN_LIBRARY";
}

#[derive(Debug, Clone, PartialEq)]
struct CommandError {
    /// Stable. Callers match on this and nothing else.
    code: &'static str,
    /// English, for a developer reading a log. Never matched on, because
    /// rewording it would then be a breaking change.
    message: String,
}

impl CommandError {
    fn new(code: &'static str, message: impl Into<String>) -> Self {
        CommandError { code, message: message.into() }
    }

    /// Whether another attempt could plausibly succeed.
    fn is_retryable(&self) -> bool {
        matches!(self.code, codes::DIGEST_MISMATCH | codes::NETWORK_UNAVAILABLE)
    }
}

impl fmt::Display for CommandError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}: {}", self.code, self.message)
    }
}

fn download(entity: &str) -> Result<u64, CommandError> {
    match entity {
        "lesson-a" => Ok(2048),
        "lesson-b" => Err(CommandError::new(
            codes::DIGEST_MISMATCH,
            "the package did not hash to what the manifest advertised",
        )),
        "lesson-c" => Err(CommandError::new(
            codes::ENTITY_NOT_IN_LIBRARY,
            "the entity is not part of any downloaded track",
        )),
        other => Err(CommandError::new(
            codes::NETWORK_UNAVAILABLE,
            format!("no route to the server while fetching {other}"),
        )),
    }
}

fn main() {
    for entity in ["lesson-a", "lesson-b", "lesson-c", "lesson-d"] {
        match download(entity) {
            Ok(bytes) => println!("{entity}: {bytes} bytes"),
            Err(error) if error.is_retryable() => {
                println!("{entity}: will retry, code {}", error.code)
            }
            Err(error) => println!("{entity}: will not retry, code {}", error.code),
        }
    }

    let error = download("lesson-b").expect_err("lesson-b always fails here");
    println!("rendered for a log: {error}");
    println!(
        "the decision used the code, not the message: {}",
        error.is_retryable() && error.code == codes::DIGEST_MISMATCH
    );
}
