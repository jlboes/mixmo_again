package com.mixmo.common;

public record VersionedResult<T>(
    T data,
    long roomVersion
) {
}

