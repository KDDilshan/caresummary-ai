package com.kavindu.caresummaryapi.ai;

import com.kavindu.caresummaryapi.ai.dto.AiResponse.CheckedLine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The most important safety rule: the AI can only cite records that really exist. */
class SourceCheckerTest {

    private static final Set<String> REAL = Set.of("Condition/1", "MedicationRequest/2", "Observation/3");

    @Test
    void lineWithRealSource_isVerified() {
        AiAnswer answer = new AiAnswer(List.of(new AiAnswer.Line("Has diabetes.", List.of("Condition/1"))));

        List<CheckedLine> lines = SourceChecker.check(answer, REAL);

        assertThat(lines).singleElement().satisfies(l -> {
            assertThat(l.verified()).isTrue();
            assertThat(l.sources()).containsExactly("Condition/1");
        });
    }

    @Test
    void madeUpSource_isRemovedAndLineMarkedUnverified() {
        AiAnswer answer = new AiAnswer(List.of(new AiAnswer.Line("Has cancer.", List.of("Condition/999"))));

        CheckedLine line = SourceChecker.check(answer, REAL).get(0);

        assertThat(line.verified()).isFalse();
        assertThat(line.sources()).isEmpty();
    }

    @Test
    void mixedSources_keepsOnlyRealOnes_andAcceptsBrackets() {
        AiAnswer answer = new AiAnswer(List.of(new AiAnswer.Line("On metformin.",
                List.of("[MedicationRequest/2]", "MedicationRequest/77", " MedicationRequest/2 "))));

        CheckedLine line = SourceChecker.check(answer, REAL).get(0);

        assertThat(line.verified()).isTrue();
        assertThat(line.sources()).containsExactly("MedicationRequest/2");
    }

    @Test
    void emptyOrNullAnswer_givesNoLines() {
        assertThat(SourceChecker.check(null, REAL)).isEmpty();
        assertThat(SourceChecker.check(new AiAnswer(null), REAL)).isEmpty();
        assertThat(SourceChecker.check(new AiAnswer(List.of(new AiAnswer.Line(" ", List.of()))), REAL)).isEmpty();
    }
}
