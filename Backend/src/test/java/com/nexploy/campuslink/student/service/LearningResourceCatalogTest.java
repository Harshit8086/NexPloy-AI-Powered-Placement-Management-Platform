package com.nexploy.campuslink.student.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexploy.campuslink.student.service.LearningResourceCatalog.LearningResource;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the curated {@link LearningResourceCatalog}: retrieval and filtering by skill, and
 * the grounding invariants that keep the AI from inventing resources — every surfaced resource has an
 * {@code https://} URL, non-blank provenance fields, and a known type/difficulty. The catalog
 * validates itself at construction, so simply building it exercises the fail-fast checks.
 */
class LearningResourceCatalogTest {

    private final LearningResourceCatalog catalog = new LearningResourceCatalog();

    @Test
    void returnsResourcesForACuratedSkillCaseInsensitively() {
        List<LearningResource> lower = catalog.resourcesFor("sql");
        List<LearningResource> mixed = catalog.resourcesFor("SQL");
        assertThat(lower).isNotEmpty();
        assertThat(mixed).isEqualTo(lower);
        assertThat(lower).allMatch(r -> "SQL".equalsIgnoreCase(r.skill()));
    }

    @Test
    void returnsEmptyForAnUncuratedOrBlankSkillRatherThanInventing() {
        assertThat(catalog.resourcesFor("Quantum Teleportation")).isEmpty();
        assertThat(catalog.hasResource("Quantum Teleportation")).isFalse();
        assertThat(catalog.resourcesFor(null)).isEmpty();
        assertThat(catalog.resourcesFor("  ")).isEmpty();
    }

    @Test
    void everySurfacedResourceIsGroundedAndWellFormed() {
        List<String> skills = List.of("REST API", "SQL", "Docker", "React", "TypeScript", "Python",
            "Kubernetes", "AWS", "Git", "Testing", "Data Structures", "Machine Learning");
        for (String skill : skills) {
            List<LearningResource> resources = catalog.resourcesFor(skill);
            assertThat(resources).as("curated resources for %s", skill).isNotEmpty();
            for (LearningResource r : resources) {
                assertThat(r.url()).as("https url").startsWith("https://");
                assertThat(r.title()).isNotBlank();
                assertThat(r.provider()).isNotBlank();
                assertThat(r.description()).isNotBlank();
                assertThat(r.enabled()).isTrue();
                assertThat(LearningResourceCatalog.TYPES).contains(r.type());
                assertThat(LearningResourceCatalog.DIFFICULTIES).contains(r.difficulty());
            }
        }
    }
}
