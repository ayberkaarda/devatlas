//! Patterns outside `match`: `if let`, `let ... else`, bindings, slices.

#[derive(Debug)]
enum Event {
    Progress { entity: String, received: i64 },
    Complete { entity: String },
    Failed { entity: String, code: String },
}

/// One arm covering three variants, because every one of them binds a field
/// with the same name and type.
fn entity_of(event: &Event) -> &str {
    match event {
        Event::Progress { entity, .. }
        | Event::Complete { entity }
        | Event::Failed { entity, .. } => entity,
    }
}

/// `let ... else` handles the failing case and has to diverge, so the binding
/// stays available for the rest of the function with no extra nesting.
fn required_number(raw: &str) -> Result<i64, String> {
    let Ok(value) = raw.parse::<i64>() else {
        return Err(format!("not a number: {raw}"));
    };
    Ok(value)
}

fn main() {
    let events = [
        Event::Progress {
            entity: String::from("lesson-a"),
            received: 512,
        },
        Event::Complete {
            entity: String::from("lesson-b"),
        },
        Event::Failed {
            entity: String::from("lesson-c"),
            code: String::from("DIGEST_MISMATCH"),
        },
    ];

    for event in &events {
        let terminal = matches!(event, Event::Complete { .. } | Event::Failed { .. });
        println!("{:<9} terminal: {}", entity_of(event), terminal);
    }

    // `if let` picks out one variant and binds its payload; the events that do
    // not match are simply skipped.
    for event in &events {
        if let Event::Progress { entity, received } = event {
            println!("{entity} has received {received} bytes");
        }
        if let Event::Failed { entity, code } = event {
            println!("{entity} failed with {code}");
        }
    }

    // `@` binds the whole value while a pattern constrains it.
    for received in [0i64, 512, 4096] {
        let note = match received {
            0 => String::from("nothing yet"),
            small @ 1..=1023 => format!("{small} bytes, under a KiB"),
            large => format!("{large} bytes"),
        };
        println!("{note}");
    }

    // Slice patterns look at position and length together.
    let path = ["content", "v13", "rust-path", "lessons"];
    match path {
        [first, .., last] => println!("from {first} to {last}"),
    }

    println!("{:?}", required_number("13"));
    println!("{:?}", required_number("thirteen"));
}
