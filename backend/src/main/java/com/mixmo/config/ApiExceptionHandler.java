package com.mixmo.config;

import com.mixmo.common.ApiEnvelope;
import com.mixmo.common.ErrorCode;
import com.mixmo.common.MixmoException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private final Clock clock;

  public ApiExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(MixmoException.class)
  public ResponseEntity<ApiEnvelope<Map<String, Object>>> handleMixmoException(MixmoException exception) {
    return ResponseEntity.status(exception.getStatus()).body(new ApiEnvelope<>(
        UUID.randomUUID().toString(),
        Instant.now(clock),
        0,
        Map.of(
            "code", exception.getCode().name(),
            "message", exception.getMessage(),
            "retryable", exception.isRetryable()
        )
    ));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiEnvelope<Map<String, Object>>> handleGenericException(Exception exception) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiEnvelope<>(
        UUID.randomUUID().toString(),
        Instant.now(clock),
        0,
        Map.of(
            "code", ErrorCode.INVALID_COMMAND.name(),
            "message", exception.getMessage(),
            "retryable", false
        )
    ));
  }
}

