package com.freshflow.insight.presentation;

/** spec-04 {@code ErrorResponse}. The same two fields the other services' controllers return. */
public record ErrorResponseDto(String error, String message) {
}
