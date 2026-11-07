package com.kavindu.caresummaryapi.ai;

import java.util.List;

/**
 * The exact JSON shape we ask the AI to return.
 * Spring AI turns the AI's JSON into this Java record for us (structured output).
 */
public record AiAnswer(List<Line> lines) {

    /**
     * @param text    one short statement, e.g. "Has type 2 diabetes since 2019."
     * @param sources the record ids that prove it, e.g. ["Condition/55"]
     */
    public record Line(String text, List<String> sources) {
    }
}
