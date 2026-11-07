package com.kavindu.caresummaryapi.ai;

import com.kavindu.caresummaryapi.ai.dto.AiResponse.CheckedLine;

import java.util.List;
import java.util.Set;

/**
 * The safety check: AI models can "hallucinate" (make things up).
 * For every line the AI wrote, we keep only the sources that really exist in the
 * patient's records. A line with no real source is marked unverified, so the doctor
 * knows not to trust it.
 */
public final class SourceChecker {

    private SourceChecker() {
    }

    public static List<CheckedLine> check(AiAnswer answer, Set<String> validSources) {
        if (answer == null || answer.lines() == null) {
            return List.of();
        }
        return answer.lines().stream()
                .filter(l -> l != null && l.text() != null && !l.text().isBlank())
                .map(l -> {
                    List<String> real = l.sources() == null ? List.of() : l.sources().stream()
                            .map(SourceChecker::clean)
                            .filter(validSources::contains)
                            .distinct()
                            .toList();
                    return new CheckedLine(l.text().trim(), real, !real.isEmpty());
                })
                .toList();
    }

    /** The AI sometimes writes "[Condition/1]" or " Condition/1 ". We accept both. */
    static String clean(String s) {
        return s == null ? "" : s.replace("[", "").replace("]", "").trim();
    }
}
