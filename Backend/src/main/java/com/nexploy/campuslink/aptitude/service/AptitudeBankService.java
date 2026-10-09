package com.nexploy.campuslink.aptitude.service;

import com.nexploy.campuslink.aptitude.dto.AddBankQuestionRequest;
import com.nexploy.campuslink.aptitude.dto.BankQuestionResponse;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestion;
import com.nexploy.campuslink.aptitude.entity.AptitudeQuestionSource;
import com.nexploy.campuslink.aptitude.exception.AptitudeQuestionNotFoundException;
import com.nexploy.campuslink.aptitude.repository.AptitudeQuestionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Officer-facing management of the curated question bank: listing, adding and soft
 * activate/deactivate. Only bank rows (SEEDED/OFFICER, no student owner) are ever surfaced here —
 * AI-generated practice questions stay private to their student. Deactivation is a soft flag: the
 * official draw filters on {@code active = true}, so a deactivated question simply stops being
 * drawn without losing its provenance.
 */
@Service
@RequiredArgsConstructor
public class AptitudeBankService {

    private static final List<AptitudeQuestionSource> BANK_SOURCES =
        List.of(AptitudeQuestionSource.SEEDED, AptitudeQuestionSource.OFFICER);

    private final AptitudeQuestionRepository questionRepository;

    /** Bank questions visible to this campus (global seeded rows plus campus-specific ones). */
    @Transactional(readOnly = true)
    public List<BankQuestionResponse> list(String campusId, String category) {
        String normalized = category == null || category.isBlank()
            ? null
            : category.trim().toUpperCase(Locale.ROOT);
        return questionRepository.findBank(BANK_SOURCES, campusId, normalized).stream()
            .map(AptitudeBankService::toResponse)
            .toList();
    }

    /** Adds an officer-curated question to the campus's bank, immediately eligible for draws. */
    @Transactional
    public BankQuestionResponse add(String campusId, AddBankQuestionRequest request) {
        String category = request.category().trim().toUpperCase(Locale.ROOT);
        if (!AptitudeTestCatalog.CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Unknown question category: " + request.category());
        }
        OffsetDateTime now = OffsetDateTime.now();
        AptitudeQuestion question = questionRepository.save(AptitudeQuestion.builder()
            .id(UUID.randomUUID().toString())
            .campusId(campusId)
            .source(AptitudeQuestionSource.OFFICER)
            .category(category)
            .difficulty(request.difficulty())
            .prompt(request.prompt())
            .optionA(request.optionA())
            .optionB(request.optionB())
            .optionC(request.optionC())
            .optionD(request.optionD())
            .correctOption(request.correctOption())
            .explanation(request.explanation())
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build());
        return toResponse(question);
    }

    /**
     * Soft activate/deactivate of a bank question. Officers may manage global seeded rows and
     * their own campus's rows; anything else reads as 404.
     */
    @Transactional
    public BankQuestionResponse setActive(String campusId, String questionId, boolean active) {
        AptitudeQuestion question = questionRepository.findById(questionId)
            .orElseThrow(() -> new AptitudeQuestionNotFoundException("Bank question not found."));
        boolean bankRow = question.getStudentId() == null
            && (question.getCampusId() == null || question.getCampusId().equals(campusId));
        if (!bankRow) {
            throw new AptitudeQuestionNotFoundException("Bank question not found.");
        }
        question.setActive(active);
        question.setUpdatedAt(OffsetDateTime.now());
        return toResponse(questionRepository.save(question));
    }

    private static BankQuestionResponse toResponse(AptitudeQuestion question) {
        return new BankQuestionResponse(
            question.getId(), question.getCategory(), question.getDifficulty(),
            question.getPrompt(), question.getOptionA(), question.getOptionB(),
            question.getOptionC(), question.getOptionD(), question.getCorrectOption(),
            question.getExplanation(), question.getSource().name(), question.isActive(),
            question.getCreatedAt());
    }
}
