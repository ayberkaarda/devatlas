package dev.bytelore.server.pipeline;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The production {@link HttpClient} bean {@link PipelineHttpClient} sends every request through. A
 * separate bean rather than something {@link PipelineHttpClient} builds for itself, so a test can
 * substitute a client with its own {@code SSLContext} (trusting a local fake HTTPS server) via
 * {@code @Primary}, without any production code path being aware a substitution is possible.
 */
@Configuration(proxyBeanMethods = false)
public class PipelineHttpClientConfig {

  @Bean
  HttpClient pipelineHttpTransport(PipelineProperties properties) {
    return HttpClient.newBuilder()
        .connectTimeout(properties.getHttpConnectTimeout())
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }
}
