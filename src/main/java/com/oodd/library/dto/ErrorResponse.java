package com.oodd.library.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Standardized JSON error payload returned by the {@code GlobalExceptionHandler}
 * for every failed API request.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
