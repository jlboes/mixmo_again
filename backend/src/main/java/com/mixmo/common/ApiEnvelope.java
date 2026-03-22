package com.mixmo.common;

import java.time.Instant;

public record ApiEnvelope<T>(
    String requestId,
    Instant serverTime,
    long roomVersion,
    T data
) {
}

