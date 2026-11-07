package com.kavindu.caresummaryapi.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/patients/{id}/ask */
public record QuestionRequest(
        @NotBlank(message = "Question is empty")
        @Size(max = 300, message = "Question is too long (max 300 characters)")
        String question
) {
}
