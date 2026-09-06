package dev.bytelore.server.pipeline;

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

  /**
   * The outcome of one confirmation attempt.
   *
   * @param passed whether the check passed
   * @param deferred true if the verify request itself answered {@code 403} or {@code 429} --
   *     signals a transient rate limit on the endpoint being asked, not a fact about the version
   *     being confirmed. A deferred outcome is always also a failed one ({@code passed} is {@code
   *     false}), but the caller must not treat it as a normal check failure: see {@link
   *     dev.bytelore.server.pipeline.PipelineItemProcessor} for why a rejection here would do
   *     permanent damage a retry could otherwise have avoided.
   * @param detail human-readable detail
   */
  public record VerifyOutcome(boolean passed, boolean deferred, String detail) {

    static VerifyOutcome passed(String detail) {
      return new VerifyOutcome(true, false, detail);
    }

    static VerifyOutcome failed(String detail) {
      return new VerifyOutcome(false, false, detail);
    }

    static VerifyOutcome deferred(String detail) {
      return new VerifyOutcome(false, true, detail);
    }
  }

  public VerifyOutcome confirm(String verifyUrlPattern, String versionString) {
    if (versionString == null || !VERSION_PATTERN.matcher(versionString).matches()) {
      return VerifyOutcome.failed(
          "Version string '%s' does not match the required pattern; it was never placed in a URL."
              .formatted(versionString));
    }

    String encoded = UriUtils.encodePathSegment(versionString, StandardCharsets.UTF_8);
    String url = verifyUrlPattern.replace("{version}", encoded);

    PipelineHttpClient.HttpFetchResult result;
    try {
      result = httpClient.get(url);
    } catch (Exception e) {
      return VerifyOutcome.failed(
          "Verify request to '%s' failed: %s".formatted(url, e.getMessage()));
    }

    if (result.statusCode() != 200) {
      // 403 and 429 are the two statuses a rate-limited API answers with (GitHub's REST API uses
      // both, depending on which limit was tripped). Treated as a signal about the endpoint's
      // current load, not about whether the claimed version is real -- see the class doing
      // something about it, dev.bytelore.server.pipeline.PipelineItemProcessor, for why a rejection
      // here instead would be a bug in its own right: it would persist a SourceUpdate row keyed by
      // this item's content hash, so a later retry -- once the rate limit clears -- would see the
      // hash as already processed and never try this item again.
      if (result.statusCode() == 403 || result.statusCode() == 429) {
        return VerifyOutcome.deferred(
            "HTTP %d from verify URL %s; treated as a transient rate limit, not a confirmation"
                + " failure -- retried on the next fetch cycle."
                    .formatted(result.statusCode(), url));
      }
      return VerifyOutcome.failed("HTTP %d from verify URL %s".formatted(result.statusCode(), url));
    }

    String normalizedBody =
        Normalizer.normalize(result.body() == null ? "" : result.body(), Normalizer.Form.NFC);
    String normalizedVersion = Normalizer.normalize(versionString, Normalizer.Form.NFC);
    if (!normalizedBody.contains(normalizedVersion)) {
      return VerifyOutcome.failed(
          "Response body from %s did not contain '%s'.".formatted(url, versionString));
    }

    return VerifyOutcome.passed("%s -> %s".formatted(url, versionString));
  }
}
