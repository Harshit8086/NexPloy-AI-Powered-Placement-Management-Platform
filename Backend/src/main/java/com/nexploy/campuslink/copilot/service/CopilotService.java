package com.nexploy.campuslink.copilot.service;

import com.nexploy.campuslink.ai.service.PromptInjectionGuard;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import com.nexploy.campuslink.copilot.dto.CopilotAnswer;
import com.nexploy.campuslink.copilot.responder.CopilotResponder;
import com.nexploy.campuslink.copilot.responder.CopilotResponder.CopilotFacts;
import com.nexploy.campuslink.student.dto.CareerRecommendationResponse;
import com.nexploy.campuslink.student.dto.StudentResponse;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import com.nexploy.campuslink.student.service.CareerRecommendationService;
import com.nexploy.campuslink.student.service.StudentService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers NEXPLOY Copilot questions grounded strictly in the authenticated user's own real data.
 *
 * <p>Every fact — readiness score, placement probability, resume score, skill gaps, open drives, top
 * match — is read from the caller's stored rows, so the Copilot never fabricates a number: if a value
 * is not on record it says so rather than inventing one. The identity is resolved server-side from the
 * security context (a student is matched by {@code email == username}), so a caller can only ever be
 * answered about themselves. The one free-text input, the question, is screened for prompt injection.
 *
 * <p>The deterministic, rule-based answer is always computed first and is authoritative. An optional
 * LLM {@link CopilotResponder} (present only when {@code nexploy.ai.provider=llm}) may rewrite it as
 * friendlier prose using only the same facts; on its absence or any failure the deterministic answer
 * is returned unchanged.
 */
@Service
@RequiredArgsConstructor
public class CopilotService {

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    /** Guards against a runaway/malformed LLM answer: anything longer keeps the deterministic text. */
    private static final int MAX_ANSWER_LENGTH = 1200;

    /** Most gap/strength skills quoted in an answer — enough to be useful, not an exhaustive dump. */
    private static final int MAX_LISTED = 3;

    private final CampusAccessService campusAccessService;
    private final StudentRepository studentRepository;
    private final StudentService studentService;
    private final CareerRecommendationService careerRecommendationService;
    private final PromptInjectionGuard promptInjectionGuard;
    // Optional LLM enhancer. Present only when nexploy.ai.provider=llm; null in the default
    // rule-based mode, in which case the deterministic answer below is returned unchanged.
    private final ObjectProvider<CopilotResponder> responderProvider;

    @Transactional(readOnly = true)
    public CopilotAnswer ask(String question) {
        UserAccount user = campusAccessService.currentUser();
        // The question is untrusted free text; screen it before it grounds an answer or reaches an LLM.
        promptInjectionGuard.verify(question);

        CopilotFacts facts = buildFacts(user);
        String deterministic = deterministicAnswer(facts, question);
        String answer = deterministic;
        String mode = "rule-based";

        CopilotResponder responder = responderProvider.getIfAvailable();
        if (responder != null) {
            try {
                String rewritten = responder.rephrase(facts, question, deterministic);
                if (valid(rewritten)) {
                    answer = rewritten.trim();
                    mode = "llm";
                }
            } catch (RuntimeException e) {
                // LLM disabled mid-flight, API error, timeout, quota, prompt-injection guard, etc.
                log.warn("Copilot responder failed; using deterministic answer. cause={}", e.toString());
            }
        }

        return new CopilotAnswer(answer, suggestions(user.getRole()), mode);
    }

    /**
     * Reads the caller's grounding facts from the database. For a STUDENT this resolves their own row
     * (by email == username) and derives non-PII numeric/skill facts plus their open-drive matches.
     * For every other role only the role is meaningful — the Copilot answers about capabilities, never
     * fabricated cross-student numbers.
     */
    private CopilotFacts buildFacts(UserAccount user) {
        String role = user.getRole() == null ? "" : user.getRole().name().toLowerCase(Locale.ROOT);
        if (user.getRole() != UserRole.STUDENT) {
            return new CopilotFacts(role, false, null, null, null, null, List.of(), List.of(), 0, null, null);
        }

        Student student = studentRepository.findByEmail(user.getUsername()).orElse(null);
        if (student == null) {
            return new CopilotFacts(role, false, null, null, null, null, List.of(), List.of(), 0, null, null);
        }

        StudentResponse profile = studentService.toResponse(student);
        List<String> topGaps = profile.criticalGaps().stream().limit(MAX_LISTED).toList();
        List<String> strengths = profile.strengths().stream().limit(MAX_LISTED).toList();

        List<CareerRecommendationResponse> matches = careerRecommendationService.recommend(student);
        CareerRecommendationResponse top = matches.isEmpty() ? null : matches.get(0);

        return new CopilotFacts(
            role,
            true,
            profile.targetRole(),
            profile.readinessScore(),
            profile.placementProbability(),
            profile.resumeScore(),
            topGaps,
            strengths,
            matches.size(),
            top == null ? null : top.role(),
            top == null ? null : top.matchScore());
    }

    /**
     * Builds the grounded, deterministic answer from the caller's real facts. Intent is matched on
     * simple keywords in the question; every number quoted comes straight from {@code facts}, and a
     * missing value is reported honestly rather than filled in.
     */
    private String deterministicAnswer(CopilotFacts facts, String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);

        if (!facts.studentGrounded()) {
            return capabilityAnswer(facts.role());
        }

        if (contains(q, "readiness", "ready")) {
            return facts.readinessScore() == null
                ? "Your readiness score is not on record yet. Complete your profile and assessments to generate it."
                : "Your readiness score is " + facts.readinessScore() + "/100" + gapsClause(facts) + ".";
        }
        if (contains(q, "probability", "chance", "improve my placement", "get placed", "likelihood")) {
            return facts.placementProbability() == null
                ? "Your placement probability is not on record yet — it appears once your readiness data is in place."
                : "Your placement probability is " + facts.placementProbability() + "%. "
                    + "Use the What-If Simulator to see how growth actions like AWS, DSA and mock interviews change it.";
        }
        if (contains(q, "resume", "cv")) {
            return facts.resumeScore() == null
                ? "Your resume score is not on record yet. Upload or complete your resume to have it analysed."
                : "Your resume scores " + facts.resumeScore() + "/100. See Resume Intelligence for the "
                    + "strengths, missing keywords and suggestions behind that score.";
        }
        if (contains(q, "skill", "gap", "missing", "learn", "weak")) {
            if (facts.topGaps().isEmpty()) {
                return "No critical skill gaps are on record for you right now. Run a Skill Gap analysis "
                    + "against a target role to check against its required skills.";
            }
            return "Your top skill gaps are " + humanList(facts.topGaps()) + ". "
                + "Open Skill Gap for the priority and a recommended action for each.";
        }
        if (contains(q, "match", "drive", "apply", "compan", "opportunit", "job", "role")) {
            if (facts.openDriveCount() == 0) {
                return "There are no open drives matched to you at your campus right now. New drives will "
                    + "appear here as soon as they open.";
            }
            String head = facts.openDriveCount() + " open drive" + (facts.openDriveCount() == 1 ? "" : "s")
                + " match your profile at your campus";
            if (facts.topMatchRole() != null && facts.topMatchScore() != null) {
                return head + ". Your strongest match is " + facts.topMatchRole() + " at "
                    + facts.topMatchScore() + "% fit — see Career Matches for the full ranking.";
            }
            return head + ". See Career Matches for the full ranking.";
        }
        if (contains(q, "eligib")) {
            return "Eligibility is a deterministic rule check per drive (CGPA, branch, backlogs, "
                + "graduation year). Open a drive to see exactly which criteria you pass or miss.";
        }
        if (contains(q, "target", "aim", "goal")) {
            return facts.targetRole() == null || facts.targetRole().isBlank()
                ? "You have not set a target role yet. Add one in your profile so recommendations and "
                    + "skill-gap analysis can align to it."
                : "Your target role is " + facts.targetRole() + gapsClause(facts) + ".";
        }
        if (contains(q, "strength", "good at", "strong")) {
            return facts.strengths().isEmpty()
                ? "No strengths are recorded on your profile yet — add them so recruiters and matching can see them."
                : "Your recorded strengths are " + humanList(facts.strengths()) + ".";
        }

        return capabilityAnswer(facts.role());
    }

    /** Trailing clause naming the top gaps, appended to an overview answer when any are on record. */
    private String gapsClause(CopilotFacts facts) {
        if (facts.topGaps().isEmpty()) {
            return "";
        }
        return ". Your biggest gaps are " + humanList(facts.topGaps());
    }

    /** Honest capability message (no numbers) for a role with no student grounding. */
    private String capabilityAnswer(String role) {
        if ("student".equals(role)) {
            return "I can answer questions about your readiness, skill gaps, placement probability, "
                + "resume and matched drives — all from your own data. Try \"Why is my readiness score?\" "
                + "or \"What skills am I missing?\"";
        }
        return "I answer questions grounded in real placement data. Detailed metrics for the "
            + (role.isBlank() ? "current" : role) + " workspace are shown on their dedicated pages; "
            + "ask about a specific student, drive or campus you have access to.";
    }

    /** Role-appropriate follow-up questions for the UI's empty state (no fabricated content). */
    private List<String> suggestions(UserRole role) {
        if (role == UserRole.STUDENT) {
            return List.of(
                "Why is my readiness score what it is?",
                "How can I improve my placement probability?",
                "What skills am I missing?",
                "What are my best-matched drives?",
                "How can I improve my resume?");
        }
        return List.of(
            "What can you help me with?",
            "How is placement data organised here?");
    }

    private boolean contains(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Joins names as "a, b and c" for readable prose. */
    private String humanList(List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    private boolean valid(String text) {
        return text != null && !text.isBlank() && text.length() <= MAX_ANSWER_LENGTH;
    }
}
