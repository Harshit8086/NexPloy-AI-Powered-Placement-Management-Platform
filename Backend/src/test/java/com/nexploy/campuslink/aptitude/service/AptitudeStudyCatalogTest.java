package com.nexploy.campuslink.aptitude.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexploy.campuslink.aptitude.service.AptitudeStudyCatalog.StudyEntry;
import com.nexploy.campuslink.aptitude.service.AptitudeStudyCatalog.StudyResource;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the curated study catalog's fail-fast contract: all four bank categories carry guidance,
 * every resource is a real https link, and malformed entries blow up at construction rather than
 * surfacing bad study guidance to students.
 */
class AptitudeStudyCatalogTest {

    private final AptitudeStudyCatalog catalog = new AptitudeStudyCatalog();

    @Test
    void everyBankCategoryHasCuratedStudyGuidance() {
        for (String category : AptitudeTestCatalog.CATEGORIES) {
            StudyEntry entry = catalog.entryFor(category).orElseThrow(
                () -> new AssertionError("Missing study entry for " + category));
            assertThat(entry.topics()).isNotEmpty();
            assertThat(entry.resources()).isNotEmpty();
            assertThat(entry.practiceTask()).isNotBlank();
            assertThat(entry.resources())
                .allSatisfy(resource -> assertThat(resource.url()).startsWith("https://"));
        }
        assertThat(catalog.entryFor("NOT_A_CATEGORY")).isEmpty();
        assertThat(catalog.entryFor(null)).isEmpty();
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertThat(catalog.entryFor("quantitative")).isPresent();
        assertThat(catalog.entryFor("Data_Interpretation")).isPresent();
    }

    @Test
    void validationRejectsNonHttpsUrls() {
        StudyEntry bad = entryWith(List.of(new StudyResource(
            "Bad link", "Provider", "http://example.com", "DOCS")));
        assertThatThrownBy(() -> AptitudeStudyCatalog.validateAndIndex(List.of(bad)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("https");
    }

    @Test
    void validationRejectsUnknownResourceTypes() {
        StudyEntry bad = entryWith(List.of(new StudyResource(
            "Bad type", "Provider", "https://example.com", "BLOG")));
        assertThatThrownBy(() -> AptitudeStudyCatalog.validateAndIndex(List.of(bad)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unknown study resource type");
    }

    @Test
    void validationRejectsBlankTopicsAndTasks() {
        StudyEntry blankTopic = new StudyEntry(
            "QUANTITATIVE", List.of("  "), List.of(resource()), "Practice task");
        assertThatThrownBy(() -> AptitudeStudyCatalog.validateAndIndex(List.of(blankTopic)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("topic");

        StudyEntry blankTask = new StudyEntry(
            "QUANTITATIVE", List.of("Percentages"), List.of(resource()), " ");
        assertThatThrownBy(() -> AptitudeStudyCatalog.validateAndIndex(List.of(blankTask)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("practiceTask");
    }

    @Test
    void validationRejectsDuplicateCategories() {
        StudyEntry first = entryWith(List.of(resource()));
        StudyEntry duplicate = entryWith(List.of(resource()));
        assertThatThrownBy(() -> AptitudeStudyCatalog.validateAndIndex(List.of(first, duplicate)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate study entry");
    }

    private StudyEntry entryWith(List<StudyResource> resources) {
        return new StudyEntry("QUANTITATIVE", List.of("Percentages"), resources, "Practice task");
    }

    private StudyResource resource() {
        return new StudyResource("Quantitative Aptitude Questions & Answers", "IndiaBIX",
            "https://www.indiabix.com/aptitude/questions-and-answers/", "INTERACTIVE");
    }
}