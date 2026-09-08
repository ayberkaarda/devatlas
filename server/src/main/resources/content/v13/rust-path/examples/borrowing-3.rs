//! Two exclusive references into one collection, and what `&mut` really means.

use std::cell::Cell;

fn double_all(values: &mut [i64]) {
    for value in values.iter_mut() {
        *value *= 2;
    }
}

/// Takes an exclusive reference and never writes through it. `&mut` is a claim
/// of exclusivity, not a promise of mutation.
fn measure(values: &mut Vec<i64>) -> usize {
    values.len()
}

fn main() {
    let mut buffer = vec![1i64, 2, 3, 4, 5, 6];

    // The library proves the two halves are disjoint, so two exclusive
    // references into one vector are sound and the compiler accepts them.
    let (left, right) = buffer.split_at_mut(3);
    double_all(left);
    right[0] = 100;
    println!("left half:  {left:?}");
    println!("right half: {right:?}");

    println!("whole buffer: {buffer:?}");
    println!("length through an unused &mut: {}", measure(&mut buffer));

    // Interior mutability: a shared reference that still mutates. The
    // exclusivity rule is upheld by `Cell` at run time instead of by the
    // compiler at compile time.
    let counter = Cell::new(0i64);
    let observer = &counter;
    observer.set(observer.get() + 1);
    observer.set(observer.get() + 1);
    println!("mutated through a shared reference: {}", counter.get() == 2);
}
