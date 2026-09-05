package dev.devatlas.server.content.packaging;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The one SHA-256 implementation in the server: lowercase hex, 64 characters (content sync protocol
 * §2, "Digest").
 *
 * <p>It is a separate public class rather than a private helper inside {@link ContentPackager}
 * because two callers need the identical procedure -- the packager, which digests a package's
 * canonical bytes at the write boundary, and the manifest, whose {@code ETag} is defined as the
 * SHA-256 of the canonical manifest bytes (§4.2). A second hand-rolled call to {@code
 * MessageDigest} somewhere else is precisely the "two implementations of one rule" the digest
 * exists to rule out.
 */
public final class Sha256 {

  private Sha256() {}

  /** Lowercase hex-encoded SHA-256 of {@code bytes}. */
  public static String hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      // Every JDK ships SHA-256; reaching this means the runtime itself is broken.
      throw new IllegalStateException("SHA-256 is not available on this JVM.", e);
    }
  }
}
