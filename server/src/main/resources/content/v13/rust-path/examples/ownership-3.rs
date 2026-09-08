//! Ownership inside a container: moving in, moving out, and the partial move.

#[derive(Debug)]
struct Entry {
    id: String,
    payload: Vec<u8>,
}

#[derive(Debug, Default)]
struct Queue {
    entries: Vec<Entry>,
    current: Option<Entry>,
}

impl Queue {
    /// `payload` belonged to the caller; after this call it belongs to the
    /// entry, which belongs to the queue.
    fn push(&mut self, id: &str, payload: Vec<u8>) {
        self.entries.push(Entry {
            id: id.to_string(),
            payload,
        });
    }

    /// Moves the front entry out of the vector and into `current`.
    fn start_next(&mut self) -> bool {
        if self.entries.is_empty() {
            return false;
        }
        let entry = self.entries.remove(0);
        self.current = Some(entry);
        true
    }

    /// Hands the current entry to the caller and leaves `None` behind, which is
    /// how a value is moved out of a field that is still borrowed.
    fn finish(&mut self) -> Option<Entry> {
        self.current.take()
    }
}

fn main() {
    let mut queue = Queue::default();
    queue.push("a", vec![1, 2, 3]);
    queue.push("b", vec![4, 5]);
    println!("queued entries: {}", queue.entries.len());

    println!("an entry started: {}", queue.start_next());
    println!("entries left in the vector: {}", queue.entries.len());
    println!("current is occupied: {}", queue.current.is_some());

    let finished = queue.finish().expect("an entry was started");
    println!("finished id: {}", finished.id);
    println!("finished payload length: {}", finished.payload.len());
    println!("current is occupied after finish: {}", queue.current.is_some());

    // Destructuring moves both fields out at once, so each has its own owner.
    let Entry { id, payload } = finished;
    println!("split into two owners: {} and {} bytes", id, payload.len());
}
