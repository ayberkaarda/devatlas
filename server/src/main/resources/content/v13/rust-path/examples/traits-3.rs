//! Implementing traits the standard library defines, and what that buys.

use std::fmt;

#[derive(Debug)]
struct StoreFailure {
    code: &'static str,
    detail: String,
}

/// `Display` is the human-readable rendering. `Debug`, derived above, is the
/// developer one, and the two are separate traits on purpose.
impl fmt::Display for StoreFailure {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "{}: {}", self.code, self.detail)
    }
}

/// A conversion the language will apply on its own at a `?` or an `.into()`.
impl From<std::num::ParseIntError> for StoreFailure {
    fn from(_error: std::num::ParseIntError) -> Self {
        StoreFailure {
            code: "INVALID_ARGUMENT",
            detail: String::from("a version column did not hold a number"),
        }
    }
}

fn stored_version(raw: &str) -> Result<i64, StoreFailure> {
    // `?` converts through the `From` impl above; nothing here mentions it.
    let version: i64 = raw.parse()?;
    Ok(version)
}

fn main() {
    let failure = StoreFailure {
        code: "STORE_UNAVAILABLE",
        detail: String::from("the database file could not be opened"),
    };

    println!("Display: {failure}");
    println!("Debug:   {failure:?}");

    // `to_string` was never implemented for this type. A blanket impl in the
    // standard library provides it for everything that implements `Display`.
    let rendered: String = failure.to_string();
    println!("to_string came from a blanket impl: {}", rendered.starts_with("STORE_UNAVAILABLE"));

    println!("parsed: {:?}", stored_version("13"));
    match stored_version("thirteen") {
        Ok(version) => println!("unexpectedly parsed {version}"),
        Err(error) => println!("converted by ?: {error}"),
    }
}
