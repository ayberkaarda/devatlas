//! The compiler enumerating the cases, and refusing to build until every one
//! is covered.

#[derive(Debug, Clone, PartialEq)]
enum QueueState {
    Queued,
    Downloading { received: i64, total: i64 },
    Verifying,
    Done,
    Failed { attempts: i64 },
}

/// No wildcard arm. Adding a variant to the enum makes this function fail to
/// compile, which is the whole reason for writing it this way.
fn label(state: &QueueState) -> String {
    match state {
        QueueState::Queued => String::from("waiting for a slot"),
        // The arm tests the shape and pulls the payload out in one step.
        QueueState::Downloading { received, total } if *total > 0 => {
            format!("{}% transferred", received * 100 / total)
        }
        QueueState::Downloading { .. } => String::from("size not yet known"),
        QueueState::Verifying => String::from("hashing what arrived"),
        QueueState::Done => String::from("complete"),
        // An or-pattern and a range, both matching on the payload.
        QueueState::Failed { attempts: 0 | 1 } => String::from("failed once"),
        QueueState::Failed { attempts: 2..=3 } => String::from("failed repeatedly"),
        QueueState::Failed { attempts } => format!("gave up after {attempts}"),
    }
}

/// `matches!` is a `match` that answers a yes-or-no question.
fn is_terminal(state: &QueueState) -> bool {
    matches!(state, QueueState::Done | QueueState::Failed { .. })
}

fn main() {
    let states = [
        QueueState::Queued,
        QueueState::Downloading { received: 512, total: 2048 },
        QueueState::Downloading { received: 0, total: 0 },
        QueueState::Verifying,
        QueueState::Done,
        QueueState::Failed { attempts: 1 },
        QueueState::Failed { attempts: 3 },
        QueueState::Failed { attempts: 9 },
    ];

    for state in &states {
        println!("{:<28} {}", label(state), is_terminal(state));
    }
}
