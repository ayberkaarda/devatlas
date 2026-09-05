package dev.devatlas.server.content.manifest;

/**
 * One entity's stored package, as it will be written to the wire.
 *
 * <p>The bytes are the ones the write boundary produced and stored (§3.3); nothing between the
 * column and the socket re-serializes them. That is the whole point of storing them: the manifest
 * advertises a digest computed over these bytes, and a response body assembled a second time from
 * the same rows would be a second chance for the two to disagree -- on every client at once, and
 * only for the entity whose owner forgot to bump a version.
 *
 * @param bytes the stored canonical package bytes
 * @param sha256Hex the stored digest over exactly those bytes, lowercase hex
 */
record ServedPackage(byte[] bytes, String sha256Hex) {

  /** The digest as a strong entity tag, quoted per RFC 9110. */
  String etag() {
    return "\"" + sha256Hex + "\"";
  }
}
