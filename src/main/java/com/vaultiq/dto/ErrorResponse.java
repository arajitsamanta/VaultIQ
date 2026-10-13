package com.vaultiq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Standard API error response payload")
public record ErrorResponse(
        @Schema(description = "Error timestamp")
        Instant timestamp,

        @Schema(description = "HTTP status code", example = "400")
        int status,

        @Schema(description = "HTTP error title", example = "Bad Request")
        String error,

        @Schema(description = "Detailed human-readable error message", example = "File is empty or unsupported format")
        String message,

        @Schema(description = "Target API endpoint path", example = "/api/documents")
        String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path);
    }
}
