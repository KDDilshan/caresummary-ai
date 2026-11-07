package com.kavindu.caresummaryapi.ai.dto;

import java.time.Instant;
import java.util.List;

/**
 * What the API returns for an AI summary or an AI answer.
 *
 * @param kind        "summary" or "answer"
 * @param question    the question asked (null for a summary)
 * @param lines       the AI's statements, each checked against the real records
 * @param unverified  how many lines had no valid source (shown as a warning in the UI)
 */
public record AiResponse(
        String patientId,
        String kind,
        String question,
        List<CheckedLine> lines,
        int unverified,
        String model,
        Instant generatedAt,
        String disclaimer
) {

    /**
     * @param verified true only if the line cites at least one record that really exists for this patient
     */
    public record CheckedLine(String text, List<String> sources, boolean verified) {
    }
}
