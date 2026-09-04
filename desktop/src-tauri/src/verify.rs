//! Package verification.
//!
//! The digest a manifest advertises covers the canonical bytes of the package
//! document. The client's whole job is to hash the bytes it received and
//! compare, so this module hashes bytes and nothing else -- it never parses,
//! re-serializes or normalizes anything first. Any transformation between the
//! wire and the hash would make the comparison meaningless.
//!
//! Packages are always served with `Content-Encoding: identity`, so the bytes on
//! disk are the bytes that were hashed on the server and `Content-Length` equals
//! the manifest's `size_bytes` exactly. That equality is what lets a truncated
//! transfer be recognised as truncated rather than as corrupt.

use std::path::Path;

use sha2::{Digest, Sha256};

/// Lowercase hex SHA-256 of a byte slice.
pub fn digest_of_bytes(bytes: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(bytes);
    hex(&hasher.finalize())
}

/// Reads a downloaded package and returns its bytes together with their
/// digest.
///
/// The bytes are returned rather than discarded so the caller parses exactly
/// what it verified. Hashing the file and then re-reading it would leave a
/// window in which the two differ, and a digest that describes something other
/// than the document being stored proves nothing at all. A package is capped at
/// 2 MiB, so holding one in memory is not a concern.
pub fn read_and_digest(path: &Path) -> std::io::Result<(Vec<u8>, String)> {
    let bytes = std::fs::read(path)?;
    let digest = digest_of_bytes(&bytes);
    Ok((bytes, digest))
}

/// Compares two digests without regard to hex case.
///
/// The protocol fixes lowercase, but a comparison that fails on case alone
/// would look exactly like corruption in a log, and chasing that once is once
/// too often.
pub fn digests_match(expected: &str, actual: &str) -> bool {
    expected.len() == actual.len() && expected.eq_ignore_ascii_case(actual)
}

fn hex(bytes: &[u8]) -> String {
    let mut out = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        use std::fmt::Write;
        let _ = write!(out, "{byte:02x}");
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    /// Known-answer test. A hash function that is wrong in a way both the
    /// producer and the consumer share would still agree with itself, so at
    /// least one value has to be checked against the published one.
    #[test]
    fn hashes_the_empty_input_to_the_published_value() {
        assert_eq!(
            digest_of_bytes(b""),
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );
    }

    #[test]
    fn a_file_is_returned_alongside_the_digest_of_the_same_bytes() {
        let dir = tempfile::tempdir().expect("temp dir");
        let path = dir.path().join("package.json");
        let bytes = br#"{"entity_type":"LESSON"}"#;
        std::fs::File::create(&path)
            .expect("create")
            .write_all(bytes)
            .expect("write");

        let (read, digest) = read_and_digest(&path).expect("read and hash");
        assert_eq!(read, bytes.to_vec());
        assert_eq!(digest, digest_of_bytes(bytes));
    }

    #[test]
    fn a_single_changed_byte_changes_the_digest() {
        assert_ne!(digest_of_bytes(b"a\n"), digest_of_bytes(b"a\r\n"));
    }

    #[test]
    fn comparison_ignores_hex_case_but_not_length() {
        let digest = digest_of_bytes(b"");
        assert!(digests_match(&digest, &digest.to_uppercase()));
        assert!(!digests_match(&digest, &digest[..63]));
    }
}
