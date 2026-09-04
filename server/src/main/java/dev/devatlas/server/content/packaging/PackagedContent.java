package dev.devatlas.server.content.packaging;

/**
 * The bytes {@link ContentPackager} produced for one write, together with the digest and length
 * computed over them -- the three columns {@code sha256}, {@code package_bytes} and {@code
 * package_size_bytes} always move together (see {@code ck_lessons_package_complete} and {@code
 * ck_mind_maps_package_complete} in {@code V2__content_model.sql}).
 *
 * @param bytes the canonical package bytes
 * @param sha256Hex lowercase hex-encoded SHA-256 digest of {@code bytes}
 * @param sizeBytes {@code bytes.length}
 */
public record PackagedContent(byte[] bytes, String sha256Hex, int sizeBytes) {}
