package com.docchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Metadata for an ingested document")
public record DocumentResponse(
        @Schema(description = "Unique document identifier", example = "doc_8f1b2c3d")
        String id,

        @Schema(description = "Name of the file uploaded", example = "quarterly-report.pdf")
        String fileName,

        @Schema(description = "MIME content type of the file", example = "application/pdf")
        String contentType,

        @Schema(description = "Size of the uploaded file in bytes", example = "1048576")
        long sizeBytes,

        @Schema(description = "Number of chunks embedded and stored into ChromaDB", example = "12")
        int chunksCount,

        @Schema(description = "Timestamp when the document was ingested")
        Instant uploadedAt
) {
}
