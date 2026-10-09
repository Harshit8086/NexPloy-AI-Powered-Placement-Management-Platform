package com.nexploy.campuslink.offer.controller;

import com.nexploy.campuslink.offer.dto.CreateOfferRequest;
import com.nexploy.campuslink.offer.dto.OfferResponse;
import com.nexploy.campuslink.offer.service.OfferService;
import com.nexploy.campuslink.shared.dto.PageRequests;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    @GetMapping
    public ResponseEntity<?> listOffers(
        @RequestParam(required = false) String campusId,
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer size,
        @RequestParam(required = false) String sort
    ) {
        if (PageRequests.requested(page, size)) {
            return ResponseEntity.ok(offerService.list(campusId, PageRequests.of(page, size, sort)));
        }
        return ResponseEntity.ok(offerService.list(campusId));
    }

    @GetMapping("/{offerId}")
    public OfferResponse getOffer(@PathVariable String offerId) {
        return offerService.get(offerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResponse extendOffer(@Valid @RequestBody CreateOfferRequest request) {
        return offerService.extend(request);
    }

    @PostMapping("/{offerId}/accept")
    public OfferResponse acceptOffer(@PathVariable String offerId) {
        return offerService.accept(offerId);
    }

    @PostMapping("/{offerId}/decline")
    public OfferResponse declineOffer(@PathVariable String offerId) {
        return offerService.decline(offerId);
    }

    @PostMapping("/{offerId}/revoke")
    public OfferResponse revokeOffer(@PathVariable String offerId) {
        return offerService.revoke(offerId);
    }

    @PostMapping("/{offerId}/nudge")
    public OfferResponse nudgeOffer(@PathVariable String offerId) {
        return offerService.nudge(offerId);
    }

    @PostMapping("/{offerId}/escalate")
    public OfferResponse escalateOffer(@PathVariable String offerId) {
        return offerService.escalate(offerId);
    }

    /** Officer/admin verifies the student's documents (Documents module gate) → funnel DOCS. */
    @PostMapping("/{offerId}/verify-documents")
    public OfferResponse verifyDocuments(@PathVariable String offerId) {
        return offerService.verifyDocuments(offerId);
    }

    /** Officer/admin confirms the student has joined → funnel JOINED (terminal). */
    @PostMapping("/{offerId}/join")
    public OfferResponse markJoined(@PathVariable String offerId) {
        return offerService.markJoined(offerId);
    }
}
