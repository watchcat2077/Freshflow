package com.freshflow.inventory.presentation;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Answers a request Spring could not even read (an unparseable JSON body, or a path or query value
 * of the wrong type) with spec-04's {@code ErrorResponse} instead of the framework's default body;
 * a translation of the framework's rejection, never a business rule (spec-01 &sect;2.5).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponseDto> malformedRequest() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponseDto(MALFORMED_REQUEST,
                        "The request could not be read. Send a well-formed JSON body whose fields "
                                + "have the documented types."));
    }
}
