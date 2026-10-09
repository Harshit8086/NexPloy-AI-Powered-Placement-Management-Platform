package com.nexploy.campuslink.skillviva.ai;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the offline tiered bank: the question set must follow {@code targetLevel}
 * (0-39 beginner, 40-69 intermediate, 70-100 advanced) so a recalibrated retake faces
 * level-appropriate questions, and every generated MCQ must be well-formed.
 */
class RuleBasedSkillVivaAiTest {

    private final RuleBasedSkillVivaAi ai = new RuleBasedSkillVivaAi();

    @Test
    void tiersChangeWithTargetLevel() {
        Assertions.assertEquals(
            "You are learning the basics of Java. What is the best way to check your understanding?",
            ai.generateQuestions("Java", 20).get(0).prompt());
        Assertions.assertEquals(
            "You know the basics of Java. What separates you from an expert?",
            ai.generateQuestions("Java", 55).get(0).prompt());
        Assertions.assertEquals(
            "Which best describes how you apply Java day to day?",
            ai.generateQuestions("Java", 70).get(0).prompt());

        // Tier boundaries: 39 stays beginner, 40/69 are intermediate, 100 is advanced.
        Assertions.assertEquals(
            ai.generateQuestions("Java", 0), ai.generateQuestions("Java", 39));
        Assertions.assertEquals(
            ai.generateQuestions("Java", 40), ai.generateQuestions("Java", 69));
        Assertions.assertEquals(
            ai.generateQuestions("Java", 70), ai.generateQuestions("Java", 100));
        Assertions.assertNotEquals(
            ai.generateQuestions("Java", 39), ai.generateQuestions("Java", 40));
    }

    @Test
    void everyQuestionIsWellFormed() {
        for (int level = 0; level <= 100; level += 25) {
            List<SkillVivaAi.VivaQuestion> questions = ai.generateQuestions("SQL", level);
            Assertions.assertEquals(5, questions.size());
            for (SkillVivaAi.VivaQuestion q : questions) {
                Assertions.assertEquals(4, q.options().size());
                Assertions.assertTrue(q.options().contains(q.correctOption()));
                Assertions.assertFalse(q.prompt().contains("{skill}"));
            }
        }
    }

    @Test
    void blankSkillFallsBackToPlaceholderText() {
        SkillVivaAi.VivaQuestion first = ai.generateQuestions("  ", 50).get(0);
        Assertions.assertTrue(first.prompt().contains("this skill"));
    }
}
