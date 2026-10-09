package com.nexploy.campuslink.offer.controller;

import com.nexploy.campuslink.offer.dto.OfferResponse;
import com.nexploy.campuslink.offer.service.OfferService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A student's offers, campus-scoped. Backs the student Offers screen. */
@RestController
@RequestMapping("/api/students/{studentId}/offers")
@RequiredArgsConstructor
public class StudentOfferController {

    private final OfferService offerService;

    @GetMapping
    public List<OfferResponse> listForStudent(@PathVariable String studentId) {
        return offerService.listByStudent(studentId);
    }
}
