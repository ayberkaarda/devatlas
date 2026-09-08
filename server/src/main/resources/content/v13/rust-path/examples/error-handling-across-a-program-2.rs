//! A chain of causes, and the boxed error a program's edge can afford.

use std::error::Error;
use std::fmt;

#[derive(Debug)]
struct ConfigError {
    code: &'static str,
}

impl fmt::Display for ConfigError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "configuration rejected ({})", self.code)
    }
}

impl Error for ConfigError {}

#[derive(Debug)]
struct StartupError {
    stage: &'static str,
    cause: ConfigError,
}

impl fmt::Display for StartupError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(formatter, "startup failed at stage {}", self.stage)
    }
}

impl Error for StartupError {
    fn source(&self) -> Option<&(dyn Error + 'static)> {
        Some(&self.cause)
    }
}

/// `Box<dyn Error>` is acceptable here because nothing above this function
/// branches on the error -- it only reports. A function whose caller has to
/// decide returns a named type instead.
fn start() -> Result<(), Box<dyn Error>> {
    Err(Box::new(StartupError {
        stage: "settings",
        cause: ConfigError { code: "INVALID_ARGUMENT" },
    }))
}

fn depth(error: &dyn Error) -> usize {
    let mut current = error;
    let mut count = 1;
    while let Some(next) = current.source() {
        current = next;
        count += 1;
    }
    count
}

fn main() {
    match start() {
        Ok(()) => println!("started"),
        Err(error) => {
            println!("reported: {error}");
            println!("links in the chain: {}", depth(error.as_ref()));

            let mut current = error.source();
            while let Some(step) = current {
                println!("caused by: {step}");
                current = step.source();
            }

            // The concrete type is still in there, which is what makes a boxed
            // error recoverable at the edge if it has to be.
            println!(
                "recovered the concrete type: {}",
                error.downcast_ref::<StartupError>().is_some()
            );
        }
    }
}
