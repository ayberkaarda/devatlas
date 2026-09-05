package dev.devatlas.server.pipeline;

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

  public HttpFetchResult get(String url) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(properties.getHttpReadTimeout())
            .header("User-Agent", "DevAtlas-Pipeline/1.0")
            .header(
                "Accept",
                "application/atom+xml, application/rss+xml, application/xml, text/xml, */*")
            .GET()
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    return new HttpFetchResult(response.statusCode(), response.body());
  }
}
