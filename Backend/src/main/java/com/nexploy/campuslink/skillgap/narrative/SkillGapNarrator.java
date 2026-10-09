package com.nexploy.campuslink.skillgap.narrative;

import java.util.List;
import java.util.Map;

/**
 * Optional enhancer that replaces the deterministic {@code whyItMatters} / {@code recommendedAction}
 * text on a skill gap with grounded, LLM-generated prose. It is the only LLM seam in the skill-gap
 * module and is present only when {@code nexploy.ai.provider=llm}; in the default rule-based mode no
 * implementation is wired and {@code SkillGapService} keeps its deterministic text.
 *
 * <p><strong>Numbers are never the LLM's job.</strong> The narrator receives only the already-computed
 * gap facts and returns text; it must not (and cannot) alter levels, gaps, priorities, labels, or
 * ordering — {@code SkillGapService} remains the system of record for every numeric value.
 *
 * <p><strong>No PII crosses this boundary.</strong> The supplied context is limited to the target
 * role and per-skill numeric facts. Student identity (id, name, email, phone), resume text, and any
 * unrelated personal data are intentionally absent.
 */
public interface SkillGapNarrator {

    /**
     * Returns enhanced narrative text keyed by skill (exact skill string as supplied). The result may
     * be empty or partial; the caller retains its deterministic text for any skill not covered, and
     * for any blank/invalid entry. Implementations should keep failures internal where possible, but
     * any thrown {@link RuntimeException} is treated by the caller as "fall back to deterministic".
     */
    Map<String, SkillNarrative> narrate(String targetRole, List<GapContext> gaps);

    /** Non-PII facts for one gap, all already computed deterministically upstream. */
    record GapContext(
        String skill,
        int currentLevel,
        int requiredLevel,
        int gap,
        String priority,
        String currentLabel,
        String targetLabel
    ) {
    }

    /** The two free-text fields the narrator may supply for a skill. */
    record SkillNarrative(
        String whyItMatters,
        String recommendedAction
    ) {
    }
}
