package com.freshflow.pricing.application;

import java.util.Optional;

/**
 * Typed outcome handed to the presentation layer for a use case with one success and one failure
 * shape.
 *
 * <p>spec-01 &sect;1.1 requires that validation be hand-rolled in the application layer and
 * "returned to the presentation layer as a typed result which the controller maps to 400 with the
 * ErrorResponse schema; the controller itself decides nothing". This is that type, in the same
 * form {@code inventory-service} uses.
 *
 * <p>It carries a failure {@code code} and {@code message} rather than an exception, so that the
 * controller performs a translation and never a judgement. A use case with more than two
 * outcomes &mdash; FR9's create, with its 201, 400, 404 and 422 &mdash; gets a sealed result of its
 * own ({@link MarkdownPlanOutcome}) rather than a code the controller would have to interpret.
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
