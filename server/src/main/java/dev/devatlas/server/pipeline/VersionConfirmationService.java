package dev.devatlas.server.pipeline;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * The {@code VERSION_CONFIRMED} check (§5.7 of the REST contract): an independent second request to
 * a whitelist source's {@code verify_url_pattern}, confirming that the version string a feed item
 * claims is real.
 *
 * <p>Passes if and only if both hold: the request returns HTTP 200, and the response body contains
 * the version string as a literal substring, compared case-sensitively after Unicode NFC
 * normalization. Status alone is worthless -- a 200 with an empty or generic body confirms nothing
 * -- and a body match alone is equally worthless -- an error page that echoes the request back
 * would "confirm" any string a caller invented.
 *
 * <p>The version string is re-validated against {@code ^[A-Za-z0-9._+-]{1,64}$} here, independently
 * of whatever produced it, and is never placed in the URL unless it passes. It is then
 * percent-encoded as a URL path segment. Both steps are required: the pattern is what stops a path
 * traversal, a scheme, an authority or whitespace from reaching URL construction; the encoding is
 * what handles the characters the pattern legitimately allows. Skipping either turns a whitelist of
 * trusted feeds into a server-side request forgery primitive driven by untrusted feed content.
 */
@Service
public class VersionConfirmationService {

  private static final Pattern VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9._+-]{1,64}$");

  private final PipelineHttpClient httpClient;

  public VersionConfirmationService(PipelineHttpClient httpClient) {
    this.httpClient = httpClient;
  }

  /** The outcome of one confirmation attempt. */
  public record VerifyOutcome(boolean passed, String detail) {}

  public VerifyOutcome confirm(String verifyUrlPattern, String versionString) {
    if (versionString == null || !VERSION_PATTERN.matcher(versionString).matches()) {
      return new VerifyOutcome(
          false,
          "Version string '%s' does not match the required pattern; it was never placed in a URL."
              .formatted(versionString));
    }

    String encoded = UriUtils.encodePathSegment(versionString, StandardCharsets.UTF_8);
    String url = verifyUrlPattern.replace("{version}", encoded);

    PipelineHttpClient.HttpFetchResult result;
    try {
      result = httpClient.get(url);
    } catch (Exception e) {
      return new VerifyOutcome(
          false, "Verify request to '%s' failed: %s".formatted(url, e.getMessage()));
    }

    if (result.statusCode() != 200) {
      return new VerifyOutcome(
          false, "HTTP %d from verify URL %s".formatted(result.statusCode(), url));
    }

    String normalizedBody =
        Normalizer.normalize(result.body() == null ? "" : result.body(), Normalizer.Form.NFC);
    String normalizedVersion = Normalizer.normalize(versionString, Normalizer.Form.NFC);
    if (!normalizedBody.contains(normalizedVersion)) {
      return new VerifyOutcome(
          false, "Response body from %s did not contain '%s'.".formatted(url, versionString));
    }

    return new VerifyOutcome(true, "%s -> %s".formatted(url, versionString));
  }
}
