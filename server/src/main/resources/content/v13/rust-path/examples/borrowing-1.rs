//! A shared reference reads a value without taking it away from its owner.

fn total(values: &[i64]) -> i64 {
    values.iter().sum()
}

fn first_word(text: &str) -> &str {
    text.split(' ').next().unwrap_or("")
}

fn main() {
    let readings = vec![3i64, 9, 4, 1];
    let sum = total(&readings);
    // `readings` was borrowed, not moved, so it is still owned right here.
    println!("sum: {sum}");
    println!("the owner survived the borrow: {}", readings.len() == 4);

    let sentence = String::from("verify then store");
    // `&String` coerces to `&str`, which is why the function can ask for the
    // narrower type and still accept this.
    println!("first word: {}", first_word(&sentence));

    // Any number of shared references may exist at the same time.
    let a = &sentence;
    let b = &sentence;
    let c = &sentence;
    println!(
        "three shared references agree on the length: {}",
        a.len() == b.len() && b.len() == c.len()
    );
    println!("still owned by main: {} bytes", sentence.len());
}
