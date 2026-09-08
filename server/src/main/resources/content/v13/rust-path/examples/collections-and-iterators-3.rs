//! Two maps, and the ordering guarantee that separates them.

use std::collections::{BTreeMap, HashMap};

fn main() {
    let observed = [
        "queued", "done", "queued", "failed", "done", "queued", "verifying",
    ];

    // The entry API looks the key up once and inserts or updates in one step.
    let mut counts: HashMap<&str, i64> = HashMap::new();
    for state in observed {
        *counts.entry(state).or_insert(0) += 1;
    }

    // A HashMap's iteration order is not specified, so printing the map itself
    // would record an accident of one run. Ask it questions instead.
    println!("distinct states: {}", counts.len());
    println!("queued seen: {}", counts.get("queued").copied().unwrap_or(0));
    println!("cancelled seen: {}", counts.get("cancelled").copied().unwrap_or(0));
    println!("contains a failed state: {}", counts.contains_key("failed"));

    // A BTreeMap is sorted by key, and that ordering is part of what it
    // promises, so printing it is reproducible on every machine.
    let ordered: BTreeMap<&str, i64> = counts.into_iter().collect();
    for (state, count) in &ordered {
        println!("{state}: {count}");
    }
    println!("keys: {:?}", ordered.keys().collect::<Vec<_>>());
    println!(
        "first and last key: {:?} {:?}",
        ordered.first_key_value().map(|(key, _)| *key),
        ordered.last_key_value().map(|(key, _)| *key)
    );

    // A range query, which only an ordered map can answer.
    let early: Vec<&&str> = ordered.range("a".."f").map(|(key, _)| key).collect();
    println!("keys before f: {early:?}");
}
