package com.docchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Citation excerpt backing the grounded answer")
public record CitationDto(
        @Schema(description = "Unique ID of the ingested document", example = "doc_8f1b2c3d")
        String documentId,

        @Schema(description = "Original filename of the document", example = "q3-financial-report.pdf")
        String documentName,

        @Schema(description = "Index of the chunk in the document", example = "2")
        int chunkIndex,

        @Schema(description = "Text excerpt retrieved from the document", example = "Net revenue increased by 14% year-over-year...")
        String text,

        @Schema(description = "Similarity relevance score from vector search", example = "0.89")
        Double relevanceScore
) {
}
