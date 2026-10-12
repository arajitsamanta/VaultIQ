package com.docchat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload for querying the RAG document chat")
public record ChatRequest(
        @Schema(description = "The question to ask over the ingested documents", example = "What are the key terms in the agreement?")
        @NotBlank(message = "Question cannot be blank")
        String question
) {
}
