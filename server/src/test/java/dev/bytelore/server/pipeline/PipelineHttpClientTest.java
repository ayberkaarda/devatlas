package dev.bytelore.server.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A pure unit test, deliberately without any Spring context: {@link
 * PipelineHttpClient#isGithubApiHost} and {@link PipelineHttpClient#buildRequest} are ordinary
 * static/instance logic over a {@link URI}, and the whole point of the host check is that it can be
 * pinned down this cheaply, without a fake server or a real network call.
 *
 * <p>This is the lock the GitHub-token rate-limit fix (see {@link VersionConfirmationService})
 * depends on: a value crafted to merely <em>contain</em> {@code api.github.com} must never be
 * treated as that host, or a feed item's own attacker-controlled {@code
 * verify_url_pattern}-adjacent text could trick the pipeline into sending its token somewhere else
 * entirely.
 */
class PipelineHttpClientTest {

  private static final String TOKEN = "ghp_UnitTestOnlyToken1234567890";

  private static PipelineHttpClient clientWithToken(String token) {
    PipelineProperties properties = new PipelineProperties();
    properties.setGithubToken(token);
    properties.setHttpReadTimeout(Duration.ofSeconds(5));
    // Never actually sent anywhere in this test class -- buildRequest() only assembles the
    // request object, it does not call HttpClient#send.
    return new PipelineHttpClient(HttpClient.newHttpClient(), properties);
  }

  static Stream<Arguments> githubHostCases() {
    return Stream.of(
        // The real host: matches.
        Arguments.of("https://api.github.com/repos/x/y/releases/tags/v1.0.0", true),
        // Host comparison is case-insensitive, like every hostname comparison.
        Arguments.of("https://API.GITHUB.COM/repos/x/y", true),
        // A subdomain that merely ends with the real host must not match.
        Arguments.of("https://api.github.com.evil.com/repos/x/y", false),
        // The real host appearing in the path of an unrelated host must not match.
        Arguments.of("https://evil.com/api.github.com/tags/v1", false),
        // The real host appearing in a query string must not match.
        Arguments.of("https://evil.com/?next=api.github.com", false),
        // github.com (the web host, source of most feed_urls) is not api.github.com.
        Arguments.of("https://github.com/repos/x/y/releases.atom", false),
        // An unrelated whitelisted host entirely.
        Arguments.of("https://go.dev/doc/go1.27", false));
  }

  @ParameterizedTest(name = "{0} -> github host = {1}")
  @MethodSource("githubHostCases")
  void isGithubApiHostMatchesOnlyTheRealHost(String url, boolean expected) {
    assertThat(PipelineHttpClient.isGithubApiHost(URI.create(url))).isEqualTo(expected);
  }

  @Test
  void buildRequestAttachesTheTokenOnlyForTheGithubApiHost() {
    PipelineHttpClient client = clientWithToken(TOKEN);

    HttpRequest githubRequest =
        client.buildRequest(URI.create("https://api.github.com/repos/x/y/releases/tags/v1.0.0"));
    assertThat(githubRequest.headers().firstValue("Authorization")).contains("Bearer " + TOKEN);

    HttpRequest evilRequest =
        client.buildRequest(URI.create("https://api.github.com.evil.com/repos/x/y"));
    assertThat(evilRequest.headers().firstValue("Authorization")).isEmpty();

    HttpRequest unrelatedRequest = client.buildRequest(URI.create("https://go.dev/doc/go1.27"));
    assertThat(unrelatedRequest.headers().firstValue("Authorization")).isEmpty();
  }

  @Test
  void buildRequestNeverAttachesAnAuthorizationHeaderWhenNoTokenIsConfigured() {
    PipelineHttpClient client = clientWithToken("");

    HttpRequest request =
        client.buildRequest(URI.create("https://api.github.com/repos/x/y/releases/tags/v1.0.0"));

    assertThat(request.headers().firstValue("Authorization")).isEmpty();
  }

  @Test
  void buildRequestNeverAttachesAnAuthorizationHeaderWhenTheTokenIsBlank() {
    PipelineHttpClient client = clientWithToken("   ");

    HttpRequest request =
        client.buildRequest(URI.create("https://api.github.com/repos/x/y/releases/tags/v1.0.0"));

    assertThat(request.headers().firstValue("Authorization")).isEmpty();
  }
}
