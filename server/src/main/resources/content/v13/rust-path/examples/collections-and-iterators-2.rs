//! Three ways to iterate, and what each of them does to ownership.

fn main() {
    let mut ids = vec![String::from("a"), String::from("b"), String::from("c")];

    // `iter` yields `&String`. The vector is borrowed and survives.
    let total_length: usize = ids.iter().map(|id| id.len()).sum();
    println!("total length: {total_length}");
    println!("still owned after iter: {}", ids.len());

    // `iter_mut` yields `&mut String` and edits in place.
    for id in ids.iter_mut() {
        id.push_str("-v13");
    }
    println!("after iter_mut: {ids:?}");

    // `for x in &collection` is `iter`, and `for x in &mut collection` is
    // `iter_mut`, spelled with the loop instead of the method.
    let mut counted = 0;
    for _ in &ids {
        counted += 1;
    }
    println!("counted through a shared loop: {counted}");

    // `into_iter` consumes the vector and yields `String` by value.
    let kept: Vec<String> = ids
        .into_iter()
        .filter(|id| id.starts_with('a') || id.starts_with('c'))
        .collect();
    // `ids` cannot be named from here: it was moved into the iterator.
    println!("kept: {kept:?}");

    // `collect` builds whatever the target type asks for.
    let joined: String = kept
        .iter()
        .map(String::as_str)
        .collect::<Vec<&str>>()
        .join(", ");
    println!("joined: {joined}");

    let lengths: Vec<usize> = kept.iter().map(String::len).collect();
    println!("lengths: {lengths:?}");
    println!("longest: {:?}", lengths.iter().max());
}
