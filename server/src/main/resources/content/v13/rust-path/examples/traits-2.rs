//! One call site, several implementations chosen at run time.

use std::sync::{Arc, Mutex};

/// Supertraits: an implementor must also be `Send` and `Sync`, which is what
/// lets an `Arc<dyn Sink>` be shared with a worker thread.
trait Sink: Send + Sync {
    fn record(&self, line: &str);
    fn recorded(&self) -> usize;
}

#[derive(Default)]
struct Collecting {
    lines: Mutex<Vec<String>>,
}

impl Sink for Collecting {
    fn record(&self, line: &str) {
        self.lines.lock().expect("not poisoned").push(line.to_string());
    }

    fn recorded(&self) -> usize {
        self.lines.lock().expect("not poisoned").len()
    }
}

struct Discarding;

impl Sink for Discarding {
    fn record(&self, _line: &str) {}

    fn recorded(&self) -> usize {
        0
    }
}

/// Dynamic dispatch: one compiled function, a call through a table of function
/// pointers carried alongside the reference.
fn drive(sink: &dyn Sink) {
    sink.record("queued");
    sink.record("downloading");
    sink.record("done");
}

fn main() {
    let collecting: Arc<dyn Sink> = Arc::new(Collecting::default());
    let discarding: Arc<dyn Sink> = Arc::new(Discarding);

    for sink in [&collecting, &discarding] {
        drive(sink.as_ref());
    }

    println!("the collecting sink kept: {}", collecting.recorded());
    println!("the discarding sink kept: {}", discarding.recorded());

    // The same value shared, not copied. Both handles see the same recording.
    let second_handle = Arc::clone(&collecting);
    second_handle.record("deleted");
    println!("both handles see the same sink: {}", collecting.recorded() == 4);
}
