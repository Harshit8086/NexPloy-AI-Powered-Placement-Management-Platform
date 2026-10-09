package com.nexploy.campuslink.shared.skill;

import java.util.Locale;
import java.util.Map;

/**
 * Canonical skill-name form used wherever skills are compared (career matching, skill-gap
 * analysis). Free-typed skill entries vary in case, plurality and abbreviation — a drive that
 * requires "Data Structures" must match a student who recorded "Data Structure" or "DSA" — so
 * every comparison keys on {@link #normalize(String)} instead of the raw text. Normalization is
 * deterministic and symmetric: both sides of a comparison pass through the same pipeline, and
 * display strings are never rewritten.
 */
public final class SkillNames {

    /**
     * Common abbreviations/spelling variants -> canonical name. Keys are matched after
     * trim/lowercase/whitespace-collapse; canonical values themselves pass through the plural
     * strip, so an alias and its plain spelling converge on the same key.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("dsa", "data structures"),
        Map.entry("oop", "object oriented programming"),
        Map.entry("oops", "object oriented programming"),
        Map.entry("os", "operating systems"),
        Map.entry("js", "javascript"),
        Map.entry("ts", "typescript"),
        Map.entry("cpp", "c++"),
        Map.entry("golang", "go"),
        Map.entry("postgres", "postgresql"),
        Map.entry("reactjs", "react"),
        Map.entry("react.js", "react"),
        Map.entry("nodejs", "node.js"),
        Map.entry("node", "node.js"),
        Map.entry("k8s", "kubernetes"),
        Map.entry("html5", "html"),
        Map.entry("css3", "css"),
        Map.entry("ml", "machine learning"),
        Map.entry("ai", "artificial intelligence"));

    private SkillNames() {
    }

    /**
     * Comparison key for a skill name: trimmed, lowercased, whitespace-collapsed, alias-resolved,
     * with a trailing plural "s" stripped ("Data Structures" == "Data Structure"). Null/blank
     * input yields "". Callers must keep the original text for display.
     */
    public static String normalize(String skill) {
        if (skill == null) {
            return "";
        }
        String key = skill.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (key.isEmpty()) {
            return "";
        }
        key = ALIASES.getOrDefault(key, key);
        // Symmetric plural strip: a trailing "s" (not "ss", not a dotted suffix like "node.js")
        // on a token long enough to keep short names such as "aws"/"css" intact. Both sides are
        // stripped identically, so comparisons stay consistent even when the residue is not a
        // dictionary word.
        if (key.length() > 3
            && key.endsWith("s")
            && !key.endsWith("ss")
            && Character.isLetter(key.charAt(key.length() - 2))) {
            key = key.substring(0, key.length() - 1);
        }
        return key;
    }
}