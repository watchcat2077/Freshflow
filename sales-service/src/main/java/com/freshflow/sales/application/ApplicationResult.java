package com.freshflow.sales.application;

import java.util.Optional;

/**
 * Typed outcome handed to the presentation layer, per spec-01 &sect;1.1: validation failures are
 * "returned to the presentation layer as a typed result which the controller maps to 400 with the
 * ErrorResponse schema; the controller itself decides nothing".
 */
public final class ApplicationResult<T> {

    private final T value;
    private final String errorCode;
    private final String errorMessage;

    private ApplicationResult(T value, String errorCode, String errorMessage) {
        this.value = value;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public static <T> ApplicationResult<T> success(T value) {
        return new ApplicationResult<>(value, null, null);
    }

    public static <T> ApplicationResult<T> failure(String errorCode, String errorMessage) {
        return new ApplicationResult<>(null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
        return errorCode == null;
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public String errorCode() {
        return errorCode;
    }

    public String errorMessage() {
        return errorMessage;
    }
}
