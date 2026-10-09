package com.nexploy.campuslink.shared.event;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end test that the domain-event pattern works through a real HTTP transaction: accepting an
 * offer publishes an {@code OfferAccepted} event, the after-commit listener records it to the
 * {@code domain_events} audit trail, and the campus-scoped read endpoint returns it under the usual
 * access rules (DESIGN.md section 7). Because the listener fires on AFTER_COMMIT, this test drives
 * the endpoint (which commits) rather than a rolled-back @Transactional test method — so the event
 * is actually delivered.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class DomainEventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OfferRepository offerRepository;

    @Autowired
    private DomainEventRecordRepository domainEventRecordRepository;

    @BeforeEach
    void reset() {
        domainEventRecordRepository.deleteAll();
        offerRepository.deleteAll();
    }

    private Authentication as(UserRole role, String campusId) {
        UserAccount user = UserAccount.builder()
            .username("u-" + role + "-" + campusId).password("{noop}secret").campusId(campusId)
            .role(role).enabled(true).build();
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private void seedExtended(String id, String campusId) {
        offerRepository.save(Offer.builder()
            .id(id).campusId(campusId).studentId("stu_1").driveId("drive_1").recruiterId("rec_1")
            .role("SDE").packageLpa(new BigDecimal("12.0")).status(OfferStatus.EXTENDED)
            .extendedAt(OffsetDateTime.parse("2026-01-01T10:00:00Z")).build());
    }

    @Test
    void acceptingAnOfferRecordsADomainEvent() throws Exception {
        seedExtended("offer_1", "campus_a");

        mockMvc.perform(post("/api/offers/offer_1/accept")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // After-commit listener ran in the same request thread, so the record exists now.
        var events = domainEventRecordRepository.findByAggregateIdOrderByOccurredAtDesc("offer_1");
        org.assertj.core.api.Assertions.assertThat(events).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(events.get(0).getEventType()).isEqualTo("OfferAccepted");
        org.assertj.core.api.Assertions.assertThat(events.get(0).getCampusId()).isEqualTo("campus_a");
    }

    @Test
    void decliningAnOfferPublishesADeclinedNotAcceptedEvent() throws Exception {
        seedExtended("offer_1", "campus_a");

        mockMvc.perform(post("/api/offers/offer_1/decline")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk());

        // Declining emits OfferDeclined (drives the recruiter notification), never OfferAccepted.
        var events = domainEventRecordRepository.findByAggregateIdOrderByOccurredAtDesc("offer_1");
        org.assertj.core.api.Assertions.assertThat(events)
            .extracting(com.nexploy.campuslink.shared.event.DomainEventRecord::getEventType)
            .containsExactly("OfferDeclined");
    }

    @Test
    void campusEventTrailIsReadableByOfficerAndScoped() throws Exception {
        seedExtended("offer_1", "campus_a");
        mockMvc.perform(post("/api/offers/offer_1/accept")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/campuses/campus_a/events")
                .with(authentication(as(UserRole.OFFICER, "campus_a"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].eventType").value("OfferAccepted"))
            .andExpect(jsonPath("$[0].aggregateId").value("offer_1"));
    }

    @Test
    void recruiterCannotReadEventTrailOfAnotherCampus() throws Exception {
        mockMvc.perform(get("/api/campuses/campus_a/events")
                .with(authentication(as(UserRole.RECRUITER, "campus_other"))))
            .andExpect(status().isForbidden());
    }
}
