package com.nexploy.campuslink.aptitude.controller;

import com.nexploy.campuslink.aptitude.dto.AddBankQuestionRequest;
import com.nexploy.campuslink.aptitude.dto.BankQuestionResponse;
import com.nexploy.campuslink.aptitude.dto.SetQuestionActiveRequest;
import com.nexploy.campuslink.aptitude.service.AptitudeBankService;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Officer-facing management of the curated aptitude question bank that official tests draw from.
 * Role-guarded in code (like the student-directory endpoints): only officers/admins reach these
 * handlers, and {@link AptitudeBankService} scopes rows to the caller's campus plus the global
 * seeded bank — AI-generated practice questions are never exposed here.
 */
@RestController
@RequestMapping("/api/aptitude/bank")
@RequiredArgsConstructor
public class OfficerAptitudeBankController {

    private final CampusAccessService campusAccessService;
    private final AptitudeBankService aptitudeBankService;

    /** Bank questions visible to the officer's campus (global seeded rows plus campus-specific). */
    @GetMapping
    public List<BankQuestionResponse> list(@RequestParam(required = false) String category) {
        UserAccount user = requireBankManager();
        return aptitudeBankService.list(user.getCampusId(), category);
    }

    /** Officer adds a curated question to their campus's bank, immediately eligible for draws. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankQuestionResponse add(@Valid @RequestBody AddBankQuestionRequest request) {
        UserAccount user = requireBankManager();
        return aptitudeBankService.add(user.getCampusId(), request);
    }

    /** Soft activate/deactivate: a deactivated question simply stops being drawn for official tests. */
    @PatchMapping("/{questionId}/active")
    public BankQuestionResponse setActive(
        @PathVariable String questionId,
        @Valid @RequestBody SetQuestionActiveRequest request
    ) {
        UserAccount user = requireBankManager();
        return aptitudeBankService.setActive(user.getCampusId(), questionId, request.active());
    }

    /** Guards the bank entry points: only officers/admins may manage the question bank. */
    private UserAccount requireBankManager() {
        UserAccount user = campusAccessService.currentUser();
        if (user.getRole() != UserRole.OFFICER && user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Only officers and admins can manage the aptitude question bank");
        }
        return user;
    }
}
