//! Failure as a value, and `?` as the early return you do not have to write.

#[derive(Debug, PartialEq)]
enum VersionError {
    Empty,
    NotANumber(String),
    OutOfRange(i64),
}

fn parse_version(raw: &str) -> Result<i64, VersionError> {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return Err(VersionError::Empty);
    }
    // `?` here unwraps the `Ok` and returns the mapped `Err` immediately.
    let value: i64 = trimmed
        .parse()
        .map_err(|_| VersionError::NotANumber(trimmed.to_string()))?;
    if value < 1 {
        return Err(VersionError::OutOfRange(value));
    }
    Ok(value)
}

fn newer_of(left: &str, right: &str) -> Result<i64, VersionError> {
    let left = parse_version(left)?;
    let right = parse_version(right)?;
    Ok(left.max(right))
}

fn main() {
    for raw in ["12", " 7 ", "", "x9", "0"] {
        match parse_version(raw) {
            Ok(version) => println!("{raw:?} accepted as version {version}"),
            Err(error) => println!("{raw:?} rejected: {error:?}"),
        }
    }

    println!("newer of 12 and 7: {:?}", newer_of("12", "7"));
    println!("newer with a bad second input: {:?}", newer_of("12", "x9"));
    println!(
        "propagation stopped at the first failure: {}",
        newer_of("x9", "") == Err(VersionError::NotANumber(String::from("x9")))
    );
}
