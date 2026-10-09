package com.nexploy.campuslink.ai.service;

import com.nexploy.campuslink.ai.exception.PromptInjectionException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Screens untrusted text (resumes, job descriptions) for content that reads as an instruction to
 * the model rather than data to extract, per SECURITY.md section 6 ("a resume or job description
 * is untrusted input and must not instruct the system to reveal secrets or bypass rules").
 *
 * <p>This is a defence-in-depth pre-filter, not a guarantee: the extraction boundary must still
 * treat all input as data. It errs toward flagging obvious override/exfiltration phrasing while
 * leaving ordinary resume/JD wording untouched.
 */
@Service
public class PromptInjectionGuard {

    private static final List<Pattern> SUSPICIOUS = List.of(
        // Instruction-override phrasing.
        compile("ignore\\s+(all\\s+|any\\s+)?(previous|prior|above|earlier)\\s+(instructions|prompts|rules)"),
        compile("disregard\\s+(the\\s+)?(previous|prior|above|system)\\b"),
        compile("forget\\s+(everything|all|your\\s+instructions)"),
        // Role / system reassignment.
        compile("you\\s+are\\s+now\\b"),
        compile("act\\s+as\\s+(a\\s+|an\\s+)?(system|admin|developer|dan)\\b"),
        compile("system\\s+prompt\\b"),
        compile("</?(system|assistant|user)>"),
        // Secret / rule exfiltration or bypass.
        compile("reveal\\s+(the\\s+)?(secret|password|token|api\\s*key|system\\s+prompt)"),
        compile("(bypass|override|ignore)\\s+(the\\s+)?(rules|security|eligibility|authorization|guardrails)"),
        compile("print\\s+(your\\s+)?(instructions|system\\s+prompt|configuration)")
    );

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /**
     * Rejects the text with {@link PromptInjectionException} if it matches a suspicious pattern.
     * Returns normally when the text looks like ordinary data.
     */
    public void verify(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (Pattern pattern : SUSPICIOUS) {
            if (pattern.matcher(text).find()) {
                throw new PromptInjectionException("matched pattern /" + pattern.pattern() + "/");
            }
        }
    }
}
