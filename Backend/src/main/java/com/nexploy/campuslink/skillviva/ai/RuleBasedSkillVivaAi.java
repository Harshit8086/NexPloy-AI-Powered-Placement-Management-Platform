package com.nexploy.campuslink.skillviva.ai;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic, offline default adapter for {@link SkillVivaAi}. Composes skill-named MCQs from
 * generic concept templates — the offline bank cannot probe a specific skill's internals, so the
 * questions test applied reasoning around "{skill}" itself. The bank is split into three tiers
 * picked by {@code targetLevel} (0-39 beginner, 40-69 intermediate, 70-100 advanced) so questions
 * are pitched at the student's current level and a retake after recalibration faces a different,
 * level-appropriate set; the LLM adapter generates deeper, skill-specific questions when {@code
 * nexploy.ai.provider=llm} activates {@link ChatClientSkillVivaAi}. The two are mutually exclusive
 * by that property, so exactly one {@code SkillVivaAi} bean exists and the app boots and tests run
 * with no API key.
 */
@Component
@ConditionalOnProperty(name = "nexploy.ai.provider", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedSkillVivaAi implements SkillVivaAi {

    /** Beginner tier (0-39): fundamentals, honest self-assessment, first steps on errors. */
    private static final List<Mcq> BEGINNER = List.of(
        new Mcq(
            "You are learning the basics of {skill}. What is the best way to check your understanding?",
            "Try a small exercise without looking at the guide, then compare your result",
            "Read the guide again and assume you understand it",
            "Copy a finished solution and call it done",
            "Ask someone to tell you the answer without trying first"),
        new Mcq(
            "What is true about someone at an early level in {skill}?",
            "They can follow simple steps and know when they need help",
            "They can skip the basics and jump straight to advanced tricks",
            "They should never make mistakes while learning",
            "They must memorise every term before touching anything"),
        new Mcq(
            "You hit an error while practising {skill}. What is the best first step?",
            "Read the error message carefully and try to understand what it says",
            "Close everything and forget about it",
            "Change random things until the error disappears",
            "Assume the tool is broken and move on"),
        new Mcq(
            "Which habit helps a beginner improve fastest at {skill}?",
            "Short, regular practice with small goals",
            "Practising once a year for a whole day",
            "Only watching others do it",
            "Jumping between ten topics at once"),
        new Mcq(
            "How should you describe your current level in {skill} honestly?",
            "Say what you can do and what you still need help with",
            "Claim expert level to impress people",
            "Say you know nothing so nobody expects anything",
            "Refuse to talk about it at all"));

    /** Intermediate tier (40-69): applied judgement, trade-offs, finishing work properly. */
    private static final List<Mcq> INTERMEDIATE = List.of(
        new Mcq(
            "You know the basics of {skill}. What separates you from an expert?",
            "Applying it in varied real situations, not just familiar exercises",
            "Knowing more keywords by heart",
            "Working faster without checking anything",
            "Never asking questions again"),
        new Mcq(
            "A small task in {skill} has two possible approaches. How do you choose?",
            "Compare the trade-offs for this task's size and constraints",
            "Always pick the most complex one to look smart",
            "Always pick whichever you used last",
            "Pick at random and hope for the best"),
        new Mcq(
            "When you get stuck in {skill}, which is the most productive move?",
            "Narrow the problem, test one change at a time, and check the docs",
            "Keep changing things at random until it works",
            "Give up and mark it as impossible",
            "Blame the tools and switch to something else"),
        new Mcq(
            "You finish a small piece of work in {skill}. What should you do before calling it done?",
            "Review it, test it, and check it against the requirements",
            "Ship it immediately and move on",
            "Delete it and start over for no reason",
            "Wait until someone else notices a problem"),
        new Mcq(
            "What shows genuine growing skill in {skill}?",
            "Explaining your choices and learning from what breaks",
            "Avoiding anything you might fail at",
            "Claiming more experience than you have",
            "Only doing tasks you have already memorised"));

    /** Advanced tier (70-100): deliberate use, trade-off explanation, debugging discipline. */
    private static final List<Mcq> ADVANCED = List.of(
        new Mcq(
            "Which best describes how you apply {skill} day to day?",
            "I use it deliberately, choosing approaches that fit the problem's constraints",
            "I only follow step-by-step guides",
            "I copy answers from searches without reading them",
            "I avoid it and let someone else handle it"),
        new Mcq(
            "A teammate asks you to explain a core concept of {skill}. What do you do?",
            "Explain the why and the trade-offs, and check their understanding",
            "Read the definition aloud without elaborating",
            "Send them a link and move on",
            "Say it is too complicated to explain"),
        new Mcq(
            "When debugging a problem in {skill}, what is your first move?",
            "Reproduce it and isolate where the behaviour breaks",
            "Restart everything and hope it clears",
            "Change random settings until something changes",
            "Leave it and wait for someone else to fix it"),
        new Mcq(
            "Which habit most reduces mistakes when working with {skill}?",
            "Small changes with review and testing as you go",
            "Shipping everything at once with no checks",
            "Skipping reviews to move faster",
            "Ignoring warnings and error messages"),
        new Mcq(
            "How would you confirm someone genuinely knows {skill}?",
            "Ask them to reason through a real scenario and defend their choices",
            "Check whether they have memorised definitions",
            "Ask how many years they claim",
            "Take their word for it"));

    @Override
    public List<VivaQuestion> generateQuestions(String skill, int targetLevel) {
        String s = (skill == null || skill.isBlank()) ? "this skill" : skill.trim();
        return tierFor(targetLevel).stream()
            .map(t -> new VivaQuestion(
                t.prompt().replace("{skill}", s),
                t.options().stream().map(o -> o.replace("{skill}", s)).toList(),
                t.correct().replace("{skill}", s)))
            .toList();
    }

    /** Maps the targeted proficiency to a difficulty tier: 0-39 / 40-69 / 70-100. */
    private static List<Mcq> tierFor(int targetLevel) {
        if (targetLevel < 40) {
            return BEGINNER;
        }
        return targetLevel < 70 ? INTERMEDIATE : ADVANCED;
    }

    private record Mcq(String prompt, String correct, String distractor1, String distractor2, String distractor3) {

        List<String> options() {
            return List.of(correct, distractor1, distractor2, distractor3);
        }
    }
}
