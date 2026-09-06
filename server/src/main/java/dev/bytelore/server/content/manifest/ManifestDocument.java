package dev.bytelore.server.content.manifest;

/**
 * A generated manifest: the canonical bytes that go on the wire, and the entity tag computed over
 * exactly those bytes.
 *
 * <p>The two are produced together and never separately. An {@code ETag} derived from anything but
 * the bytes actually sent -- a re-serialization, a timestamp, a row version -- would let a {@code
 * 304} claim "nothing changed" about a representation the client never held.
 *
 * @param bytes the canonical manifest bytes, UTF-8, no trailing newline
 * @param etag the quoted, strong entity tag: {@code "<sha256 of bytes>"}
 */
public record ManifestDocument(byte[] bytes, String etag) {}
