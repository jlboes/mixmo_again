package com.mixmo.common;

import org.springframework.http.HttpStatus;

public class MixmoException extends RuntimeException {
  private final ErrorCode code;
  private final HttpStatus status;
  private final boolean retryable;

  public MixmoException(ErrorCode code, String message, HttpStatus status) {
    this(code, message, status, false);
  }

  public MixmoException(ErrorCode code, String message, HttpStatus status, boolean retryable) {
    super(message);
    this.code = code;
    this.status = status;
    this.retryable = retryable;
  }

  public ErrorCode getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public boolean isRetryable() {
    return retryable;
  }
}

