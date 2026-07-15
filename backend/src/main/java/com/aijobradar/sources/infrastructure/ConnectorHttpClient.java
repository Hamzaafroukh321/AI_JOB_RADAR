package com.aijobradar.sources.infrastructure;

import com.aijobradar.sources.application.UnsafeSourceException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConnectorHttpClient {
  private static final int MAX_RESPONSE_BYTES = 5_000_000;
  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final SafeUrlPolicy urls;

  public ConnectorHttpClient(SafeUrlPolicy urls) {
    this.urls = urls;
  }

  public Response get(String value, Map<String, String> headers) {
    URI uri = urls.requirePublicHttpUrl(value);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("User-Agent", "AI-Job-Radar/1.0 source-connector")
            .GET();
    headers.forEach(request::header);
    try {
      HttpResponse<InputStream> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      int status = response.statusCode();
      if (status >= 300 && status < 400)
        throw new UnsafeSourceException("REDIRECT_BLOCKED", "Source redirect requires review");
      byte[] body;
      try (InputStream stream = response.body()) {
        body = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
      }
      if (body.length > MAX_RESPONSE_BYTES)
        throw new UnsafeSourceException("CONTENT_TOO_LARGE", "Source response exceeds limit");
      if (status == 429)
        throw new UnsafeSourceException("RATE_LIMITED", "Source rate limit reached");
      if (status < 200 || status >= 300)
        throw new UnsafeSourceException(
            status >= 500 ? "HTTP_5XX" : "HTTP_4XX", "Source returned HTTP " + status);
      return new Response(status, new String(body, StandardCharsets.UTF_8));
    } catch (UnsafeSourceException exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new UnsafeSourceException("INTERRUPTED", "Source request was interrupted");
    } catch (Exception exception) {
      throw new UnsafeSourceException("NETWORK_ERROR", "Source request failed");
    }
  }

  public record Response(int status, String body) {}
}
