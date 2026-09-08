//! What a move copies, what it leaves alone, and where `Copy` changes the rule.

#[derive(Debug, Clone, Copy, PartialEq)]
struct Version {
    content_version: i64,
}

#[derive(Debug, Clone, PartialEq)]
struct Package {
    id: String,
    bytes: Vec<u8>,
}

fn takes_copy(version: Version) -> i64 {
    version.content_version
}

fn takes_ownership(package: Package) -> usize {
    package.bytes.len()
}

fn main() {
    let version = Version { content_version: 7 };
    let echoed = takes_copy(version);
    // `version` is still usable: `Version` is `Copy`, so the call duplicated it
    // instead of handing it over.
    println!(
        "a Copy value is usable after the call: {}",
        version.content_version == echoed
    );

    let package = Package {
        id: String::from("lesson-1"),
        bytes: vec![1, 2, 3, 4],
    };
    let duplicate = package.clone();
    println!("clone compares equal to the original: {}", duplicate == package);

    let size = takes_ownership(package);
    // `package` has moved into the function. `duplicate` is a separate value
    // with its own buffer and is untouched by that.
    println!("the moved value reported size: {size}");
    println!(
        "the clone outlived the move of the original: {}",
        duplicate.bytes.len() == size
    );

    // A move does not touch the heap buffer. The same allocation is simply
    // described by a different name afterwards.
    let mut buffer: Vec<u8> = Vec::with_capacity(64);
    buffer.push(1);
    let capacity_before = buffer.capacity();
    let relocated = buffer;
    println!(
        "capacity is unchanged by the move: {}",
        relocated.capacity() == capacity_before
    );
    println!(
        "capacity is at least what was requested: {}",
        relocated.capacity() >= 64
    );
}
