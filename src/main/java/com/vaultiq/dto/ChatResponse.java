package com.vaultiq.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response containing the grounded answer and supporting citations")
public record ChatResponse(
        @Schema(description = "The original question asked", example = "What was the Q3 revenue growth?")
        String question,

        @Schema(description = "The generated answer based on retrieved document chunks", example = "According to the Q3 report, revenue grew by 14% year-over-year.")
        String answer,

        @Schema(description = "List of cited document chunks used to synthesize the answer")
        List<CitationDto> citations
) {
}
