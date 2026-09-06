package dev.bytelore.server.pipeline;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.stereotype.Component;

/**
 * The one HTTP client the pipeline uses, for both the feed fetch and the independent second
 * "confirm the version" request (§5.7 of the REST contract).
 *
 * <p>Both calls are bounded by a short timeout -- feeds and verify endpoints are external services
 * the pipeline does not control, and a hung request must not hold a manual fetch trigger open
 * indefinitely (§5.7: "the request does not hang indefinitely") or block the scheduled job's next
 * source. Redirects are followed, because a redirect chain ending in {@code 200} is a pass and a
 * redirect chain ending anywhere else is a fail -- the {@link java.net.http.HttpClient} handles the
 * chain, and this class only ever reports the final outcome.
 *
 * <p>The underlying {@link HttpClient} is an injected bean ({@link PipelineHttpClientConfig})
 * rather than one this class builds for itself, specifically so a test can substitute a client with
 * a permissive {@code SSLContext} against a local fake HTTPS server without touching production
 * wiring -- the same pattern the request-scoped {@link java.time.Clock} bean uses elsewhere in this
 * server.
 */
@Component
public class PipelineHttpClient {

  private final HttpClient client;
  private final PipelineProperties properties;

  public PipelineHttpClient(HttpClient pipelineHttpTransport, PipelineProperties properties) {
    this.client = pipelineHttpTransport;
    this.properties = properties;
  }

  /** The HTTP outcome of one request: final status code and body, after any redirects. */
  public record HttpFetchResult(int statusCode, String body) {}

  /**
   * The one host a configured GitHub token is ever attached to. Compared against {@link
   * URI#getHost()}, never against the raw URL string, specifically so a value crafted to merely
   * *contain* this host -- as a subdomain ({@code api.github.com.evil.com}), or as path/query text
   * on an unrelated host ({@code evil.com/api.github.com}) -- cannot pass: {@link URI} parses the
   * authority component once, correctly, and a host comparison against its result cannot be fooled
   * by where else the substring appears in the string.
   */
  private static final String GITHUB_API_HOST = "api.github.com";

  public HttpFetchResult get(String url) throws IOException, InterruptedException {
    HttpRequest request = buildRequest(URI.create(url));
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return new HttpFetchResult(response.statusCode(), response.body());
  }

  /**
   * Package-private so a test can assert on the built request directly -- header presence and
   * absence alike -- without sending it anywhere.
   */
  HttpRequest buildRequest(URI uri) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri)
            .timeout(properties.getHttpReadTimeout())
            .header("User-Agent", "ByteLore-Pipeline/1.0")
            .header(
                "Accept",
                "application/atom+xml, application/rss+xml, application/xml, text/xml, */*")
            .GET();
    String token = properties.getGithubToken();
    if (token != null && !token.isBlank() && isGithubApiHost(uri)) {
      // Cross-host redirects (java.net.http.HttpClient, followRedirects=NORMAL) already drop every
      // request header including this one before replaying the request against the new host, so no
      // further check is needed at the point a redirect is followed -- only at the point this
      // header is first attached.
      builder.header("Authorization", "Bearer " + token);
    }
    return builder.build();
  }

  static boolean isGithubApiHost(URI uri) {
    String host = uri.getHost();
    return host != null && GITHUB_API_HOST.equalsIgnoreCase(host);
  }
}
