package com.nexploy.campuslink.skillviva.service;

import com.nexploy.campuslink.ai.service.PromptInjectionGuard;
import com.nexploy.campuslink.skillviva.ai.SkillVivaAi;
import com.nexploy.campuslink.skillviva.dto.StartVivaResponse;
import com.nexploy.campuslink.skillviva.dto.SubmitVivaRequest;
import com.nexploy.campuslink.skillviva.dto.SubmitVivaResponse;
import com.nexploy.campuslink.skillviva.dto.VivaQuestionResponse;
import com.nexploy.campuslink.student.entity.StudentSkill;
import com.nexploy.campuslink.student.service.StudentSkillService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Runs a short, skill-scoped AI viva that can promote one of the student's own skills to VERIFIED.
 * {@link #start} returns AI-generated MCQs (options shuffled, correct answers withheld) and keeps a
 * short-lived in-memory copy of the exact set keyed by a fresh {@code vivaId}; the client picks an
 * option per question and echoes the prompts plus that {@code vivaId} back to {@link #submit}, which
 * scores each pick against the stored copy, averages, and recalibrates the skill to the measured
 * level. Ownership is enforced by {@link StudentSkillService} (student resolved by email), and the
 * skill label is screened by {@link PromptInjectionGuard}.
 *
 * <p>Scoring against the start copy matters because the LLM adapter is non-deterministic: regenerating
 * the set on submit could produce different prompts and mark honest answers as unknown. When no (or an
 * expired/foreign) {@code vivaId} is presented the service falls back to regeneration, which is exact
 * for the deterministic rule-based adapter. The stored copy is one-shot and expires after
 * {@link #VIVA_TTL}; a durable viva table and signing the generated questions against a tampering
 * client are deliberate phase-2 hardenings.
 */
@Service
@RequiredArgsConstructor
public class SkillVivaService {

    /** Fallback target level when a skill somehow has no recorded proficiency. */
    private static final int DEFAULT_LEVEL = 50;

    /** Seconds per question; MCQs read faster than typed answers but deserve a comfortable budget. */
    private static final int TIMER_SECONDS = 45;

    /** How long an unsubmitted viva set stays scoreable; a viva is a sitting, not a standing offer. */
    private static final Duration VIVA_TTL = Duration.ofMinutes(30);

    private final SkillVivaAi skillVivaAi;
    private final StudentSkillService studentSkillService;
    private final PromptInjectionGuard promptInjectionGuard;

    /** In-flight viva sets keyed by vivaId — the exact questions each student was shown at /start. */
    private final Map<String, StoredViva> openVivas = new ConcurrentHashMap<>();

    public StartVivaResponse start(String skillId) {
        StudentSkill skill = studentSkillService.getOwnedSkill(skillId); // ownership-checked
        String skillName = skill.getSkill();
        promptInjectionGuard.verify(skillName);

        int targetLevel = level(skill); // questions target the student's claimed / last-verified level
        List<SkillVivaAi.VivaQuestion> generated =
            List.copyOf(skillVivaAi.generateQuestions(skillName, targetLevel));

        pruneExpired();
        String vivaId = UUID.randomUUID().toString();
        openVivas.put(vivaId, new StoredViva(skill.getId(), generated, Instant.now()));

        List<VivaQuestionResponse> questions = new ArrayList<>();
        int ordinal = 0;
        for (SkillVivaAi.VivaQuestion q : generated) {
            List<String> shuffled = new ArrayList<>(q.options());
            Collections.shuffle(shuffled); // position carries no information for the client
            questions.add(new VivaQuestionResponse(ordinal++, q.prompt(), List.copyOf(shuffled), TIMER_SECONDS));
        }
        return new StartVivaResponse(vivaId, skill.getId(), skillName, targetLevel, List.copyOf(questions));
    }

    public SubmitVivaResponse submit(String skillId, SubmitVivaRequest request) {
        StudentSkill skill = studentSkillService.getOwnedSkill(skillId);
        String skillName = skill.getSkill();
        int claimedLevel = level(skill);

        // Score against the copy generated at /start when the client echoes its vivaId: the LLM
        // adapter is non-deterministic, so regenerating now could yield different prompts and mark
        // honest answers unknown. Without a usable vivaId fall back to regeneration — exact for the
        // deterministic rule-based adapter.
        StoredViva stored = takeStored(skillId, request.vivaId());
        List<SkillVivaAi.VivaQuestion> source = stored != null
            ? stored.questions()
            : skillVivaAi.generateQuestions(skillName, claimedLevel);

        // Keyed by normalized prompt so a submitted answer can be matched to the question it belongs
        // to without trusting the client's text.
        Map<String, SkillVivaAi.VivaQuestion> byPrompt = new LinkedHashMap<>();
        for (SkillVivaAi.VivaQuestion q : source) {
            byPrompt.putIfAbsent(key(q.prompt()), q);
        }

        List<SubmitVivaResponse.AnswerFeedback> feedback = new ArrayList<>();
        int total = 0;
        for (SubmitVivaRequest.Answer a : request.answers()) {
            SkillVivaAi.VivaQuestion question = byPrompt.get(key(a.question()));
            if (question == null) {
                // Unknown prompt (tampered/stale client): score 0.
                feedback.add(new SubmitVivaResponse.AnswerFeedback(a.question(), 0, null));
                continue;
            }
            // Score by option TEXT, not position: the client saw a shuffled option list, and the text
            // is the only identifier that survives that shuffle without trusting the client's ordering.
            int score = question.correctOption().equals(a.option()) ? 100 : 0;
            total += score;
            feedback.add(new SubmitVivaResponse.AnswerFeedback(a.question(), score, question.correctOption()));
        }
        int achievedLevel = request.answers().isEmpty()
            ? 0
            : Math.round((float) total / request.answers().size());

        // Recalibrate the stored skill to the measured level (up or down) and mark it VERIFIED, so the
        // UI shows the real level and the next viva targets it.
        String status = studentSkillService.recordVivaLevel(skillId, achievedLevel).getVerificationStatus();
        return new SubmitVivaResponse(achievedLevel, claimedLevel, status, List.copyOf(feedback));
    }

    /** One-shot lookup: removes the stored set and accepts it only if it is for this skill and fresh. */
    private StoredViva takeStored(String skillId, String vivaId) {
        if (vivaId == null || vivaId.isBlank()) {
            return null;
        }
        StoredViva stored = openVivas.remove(vivaId);
        if (stored == null || !stored.skillId().equals(skillId) || expired(stored.startedAt())) {
            return null;
        }
        return stored;
    }

    private void pruneExpired() {
        Iterator<Map.Entry<String, StoredViva>> it = openVivas.entrySet().iterator();
        while (it.hasNext()) {
            if (expired(it.next().getValue().startedAt())) {
                it.remove();
            }
        }
    }

    private static boolean expired(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).compareTo(VIVA_TTL) > 0;
    }

    private static String key(String prompt) {
        return prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static int level(StudentSkill skill) {
        Integer p = skill.getProficiency();
        if (p == null) {
            return DEFAULT_LEVEL;
        }
        return Math.max(0, Math.min(100, p));
    }

    /** One in-flight viva set: the questions exactly as generated at /start, keyed for scoring. */
    private record StoredViva(String skillId, List<SkillVivaAi.VivaQuestion> questions, Instant startedAt) {
    }
}
