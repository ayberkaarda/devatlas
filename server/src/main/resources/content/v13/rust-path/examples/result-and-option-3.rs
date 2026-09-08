//! A sequence of fallible steps, gathered into one answer.

#[derive(Debug, PartialEq)]
struct Rejected {
    code: &'static str,
    input: String,
}

fn parse_one(raw: &str) -> Result<i64, Rejected> {
    raw.parse::<i64>().map_err(|_| Rejected {
        code: "NOT_A_NUMBER",
        input: raw.to_string(),
    })
}

/// `collect` into a `Result` short-circuits: the first failure is the answer
/// and the rest of the input is never parsed.
fn parse_all(raw: &[&str]) -> Result<Vec<i64>, Rejected> {
    raw.iter().map(|item| parse_one(item)).collect()
}

fn main() {
    println!("every input valid: {:?}", parse_all(&["1", "2", "3"]));
    println!("one input invalid: {:?}", parse_all(&["1", "two", "3"]));

    // Keeping both sides instead of short-circuiting.
    let (accepted, rejected): (Vec<_>, Vec<_>) = ["1", "two", "3", "four"]
        .iter()
        .map(|item| parse_one(item))
        .partition(Result::is_ok);
    println!("accepted: {}", accepted.len());
    println!("rejected: {}", rejected.len());

    // Moving between the two types when the boundary demands one of them.
    let present: Option<i64> = Some(3);
    println!("option to result: {:?}", present.ok_or("MISSING"));

    let failure: Result<i64, &str> = Err("MISSING");
    println!("result to option: {:?}", failure.ok());
    println!(
        "a default computed only when needed: {}",
        failure.unwrap_or_else(|code| code.len() as i64)
    );
}
