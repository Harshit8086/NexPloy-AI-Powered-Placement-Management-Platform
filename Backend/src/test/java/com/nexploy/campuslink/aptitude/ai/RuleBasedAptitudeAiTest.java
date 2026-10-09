package com.nexploy.campuslink.aptitude.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the offline rule-based adapter: curated practice generation (count, cycling,
 * normalization, fallback) and the transparent threshold analysis (strength at >=70%, weakness at
 * <50%, silence in between).
 */
class RuleBasedAptitudeAiTest {

    private final RuleBasedAptitudeAi ai = new RuleBasedAptitudeAi();

    @Test
    void generatesTheRequestedCountByCyclingTheCuratedBank() {
        List<AptitudeQuestionDraft> drafts = ai.generatePracticeQuestions("QUANTITATIVE", 8);

        assertThat(drafts).hasSize(8);
        for (AptitudeQuestionDraft draft : drafts) {
            assertThat(draft.prompt()).isNotBlank();
            assertThat(draft.optionA()).isNotBlank();
            assertThat(draft.optionB()).isNotBlank();
            assertThat(draft.optionC()).isNotBlank();
            assertThat(draft.optionD()).isNotBlank();
            assertThat(draft.correctOption()).isIn("A", "B", "C", "D");
        }
        // Six curated questions, so the seventh wraps to the first.
        assertThat(drafts.get(6).prompt()).isEqualTo(drafts.get(0).prompt());
    }

    @Test
    void normalizesCategoryCaseAndFallsBackForUnknownCategories() {
        assertThat(ai.generatePracticeQuestions("quantitative", 3)).hasSize(3);
        // Unknown categories degrade to the quantitative bank rather than failing the practice lane.
        assertThat(ai.generatePracticeQuestions("SPACE_OPERA", 3)).hasSize(3);
    }

    @Test
    void analysisSplitsSectionsByThreshold() {
        AptitudeAnalysis analysis = ai.analyze(new AptitudeAttemptStats(
            "Quantitative Aptitude", 30, 18, 600, 900, List.of(
                new AptitudeAttemptStats.CategoryStats("QUANTITATIVE", 10, 8),
                new AptitudeAttemptStats.CategoryStats("LOGICAL", 10, 4),
                new AptitudeAttemptStats.CategoryStats("VERBAL", 10, 6))));

        assertThat(analysis.strengths()).anySatisfy(entry -> assertThat(entry).contains("quantitative"));
        assertThat(analysis.weaknesses()).anySatisfy(entry -> assertThat(entry).contains("logical"));
        // 60% sits between the thresholds: neither a strength nor a weakness.
        assertThat(analysis.strengths()).noneSatisfy(entry -> assertThat(entry).contains("verbal"));
        assertThat(analysis.weaknesses()).noneSatisfy(entry -> assertThat(entry).contains("verbal"));
        assertThat(analysis.tips()).isNotEmpty();
        assertThat(analysis.summary()).contains("18").contains("30");
    }

    @Test
    void anAllWeakAttemptStillReportsAnEncouragingStrength() {
        AptitudeAnalysis analysis = ai.analyze(new AptitudeAttemptStats(
            "Logical Reasoning", 10, 2, 300, 900, List.of(
                new AptitudeAttemptStats.CategoryStats("LOGICAL", 10, 2))));

        assertThat(analysis.weaknesses()).hasSize(1);
        assertThat(analysis.strengths()).containsExactly("Completed the attempt end to end");
    }
}