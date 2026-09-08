//! One error type per layer, and the conversions that feed it.

use std::fmt;
use std::num::ParseIntError;

#[derive(Debug)]
enum StoreError {
    Malformed(String),
    MissingField(&'static str),
    BadNumber(ParseIntError),
}

/// The developer-facing rendering. Fixed wording, so a log line does not change
/// when the standard library rephrases something underneath.
impl fmt::Display for StoreError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            StoreError::Malformed(row) => write!(formatter, "malformed row: {row}"),
            StoreError::MissingField(name) => write!(formatter, "missing field: {name}"),
            StoreError::BadNumber(_) => {
                write!(formatter, "a numeric field did not hold a number")
            }
        }
    }
}

impl std::error::Error for StoreError {
    /// The underlying failure, kept rather than flattened into a string.
    fn source(&self) -> Option<&(dyn std::error::Error + 'static)> {
        match self {
            StoreError::BadNumber(inner) => Some(inner),
            _ => None,
        }
    }
}

/// This impl is what makes `?` able to convert. Nothing at the call site
/// mentions it.
impl From<ParseIntError> for StoreError {
    fn from(error: ParseIntError) -> Self {
        StoreError::BadNumber(error)
    }
}

fn parse_row(row: &str) -> Result<(String, i64), StoreError> {
    let (id, version) = row
        .split_once('=')
        .ok_or_else(|| StoreError::Malformed(row.to_string()))?;
    if id.is_empty() {
        return Err(StoreError::MissingField("id"));
    }
    let version: i64 = version.trim().parse()?;
    Ok((id.to_string(), version))
}

fn main() {
    for row in ["lesson-a=13", "=4", "lesson-b", "lesson-c=x"] {
        match parse_row(row) {
            Ok((id, version)) => println!("{row:?} -> {id} at version {version}"),
            Err(error) => println!("{row:?} -> {error}"),
        }
    }

    let failure = parse_row("lesson-c=x").expect_err("a non-numeric version is rejected");
    println!(
        "the conversion kept an underlying cause: {}",
        std::error::Error::source(&failure).is_some()
    );
}
