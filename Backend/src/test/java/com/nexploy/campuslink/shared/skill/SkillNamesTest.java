package com.nexploy.campuslink.shared.skill;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SkillNamesTest {

    @Test
    void pluralAndSingularFormsNormalizeToSameKey() {
        assertThat(SkillNames.normalize("Data Structures"))
            .isEqualTo(SkillNames.normalize("Data Structure"));
        assertThat(SkillNames.normalize("data structures"))
            .isEqualTo(SkillNames.normalize("DATA STRUCTURE"));
    }

    @Test
    void commonAliasesResolveToCanonicalSkill() {
        assertThat(SkillNames.normalize("DSA")).isEqualTo(SkillNames.normalize("Data Structures"));
        assertThat(SkillNames.normalize("JS")).isEqualTo(SkillNames.normalize("JavaScript"));
        assertThat(SkillNames.normalize("Cpp")).isEqualTo(SkillNames.normalize("C++"));
        assertThat(SkillNames.normalize("K8s")).isEqualTo(SkillNames.normalize("Kubernetes"));
        assertThat(SkillNames.normalize("Postgres")).isEqualTo(SkillNames.normalize("PostgreSQL"));
        assertThat(SkillNames.normalize("React.js")).isEqualTo(SkillNames.normalize("React"));
    }

    @Test
    void caseWhitespaceAndPaddingDoNotAffectKey() {
        assertThat(SkillNames.normalize("  Spring   Boot "))
            .isEqualTo(SkillNames.normalize("spring boot"));
    }

    @Test
    void shortTokensEndingInSAreKeptWhole() {
        // "aws"/"css" sit under the plural-strip threshold and must not lose their final "s".
        assertThat(SkillNames.normalize("aws")).isEqualTo("aws");
        assertThat(SkillNames.normalize("css")).isEqualTo("css");
    }

    @Test
    void dottedNamesKeepTheirFinalS() {
        assertThat(SkillNames.normalize("node.js")).isEqualTo(SkillNames.normalize("NodeJS"));
    }

    @Test
    void distinctSkillsStayDistinct() {
        assertThat(SkillNames.normalize("Java")).isNotEqualTo(SkillNames.normalize("JavaScript"));
        assertThat(SkillNames.normalize("SQL")).isNotEqualTo(SkillNames.normalize("NoSQL"));
    }

    @Test
    void nullAndBlankNormalizeToEmpty() {
        assertThat(SkillNames.normalize(null)).isEmpty();
        assertThat(SkillNames.normalize("   ")).isEmpty();
    }
}