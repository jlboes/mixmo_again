package com.mixmo.grid;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mixmo.common.ErrorCode;
import com.mixmo.common.MixmoException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class HttpPythonWordValidatorClient implements PythonWordValidatorClient {

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final URI batchEndpoint;
  private final Duration timeout;

  public HttpPythonWordValidatorClient(
      ObjectMapper objectMapper,
      @Value("${mixmo.validator.base-url:http://localhost:8000}") String baseUrl,
      @Value("${mixmo.validator.timeout:1500ms}") Duration timeout
  ) {
    this.objectMapper = objectMapper;
    this.timeout = timeout;
    this.httpClient = HttpClient.newBuilder()
        .connectTimeout(timeout)
        .version(HttpClient.Version.HTTP_1_1)
        .build();
    this.batchEndpoint = URI.create(trimTrailingSlash(baseUrl) + "/validate/batch");
  }

  @Override
  public List<GridValidationModels.PythonValidationResult> validateBatch(List<String> words) {
    if (words == null || words.isEmpty()) {
      return List.of();
    }

    try {
      String requestBody = objectMapper.writeValueAsString(Map.of("words", words));
      HttpRequest request = HttpRequest.newBuilder(batchEndpoint)
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .timeout(timeout)
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 400) {
        throw unavailable(
            "Word validator returned HTTP " + response.statusCode() + ": " + response.body(),
            HttpStatus.BAD_GATEWAY,
            true
        );
      }

      return objectMapper.readValue(response.body(), new TypeReference<List<GridValidationModels.PythonValidationResult>>() {
      });
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw unavailable("Word validator request was interrupted.", HttpStatus.SERVICE_UNAVAILABLE, true);
    } catch (IOException exception) {
      throw unavailable("Word validator is unavailable.", HttpStatus.SERVICE_UNAVAILABLE, true);
    }
  }

  private MixmoException unavailable(String message, HttpStatus status, boolean retryable) {
    return new MixmoException(ErrorCode.VALIDATION_SERVICE_UNAVAILABLE, message, status, retryable);
  }

  private String trimTrailingSlash(String baseUrl) {
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
