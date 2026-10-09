package com.nexploy.campuslink.copilot.controller;

import com.nexploy.campuslink.copilot.dto.CopilotAnswer;
import com.nexploy.campuslink.copilot.dto.CopilotRequest;
import com.nexploy.campuslink.copilot.service.CopilotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * NEXPLOY Copilot Q&A. Answers are grounded strictly in the authenticated caller's own real data
 * (resolved server-side, never from the request), so the assistant never fabricates numbers. The
 * question is screened for prompt injection in the service before it grounds an answer or reaches an
 * optional LLM.
 */
@RestController
@RequestMapping("/api/copilot")
@RequiredArgsConstructor
public class CopilotController {

    private final CopilotService copilotService;

    @PostMapping("/ask")
    public CopilotAnswer ask(@Valid @RequestBody CopilotRequest request) {
        return copilotService.ask(request.question());
    }
}
