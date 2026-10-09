package com.nexploy.campuslink.student.service;

import com.nexploy.campuslink.application.entity.Application;
import com.nexploy.campuslink.application.entity.ApplicationStatus;
import com.nexploy.campuslink.application.repository.ApplicationRepository;
import com.nexploy.campuslink.offer.entity.Offer;
import com.nexploy.campuslink.offer.entity.OfferStatus;
import com.nexploy.campuslink.offer.repository.OfferRepository;
import com.nexploy.campuslink.student.entity.Student;
import com.nexploy.campuslink.student.repository.StudentRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives a student's coarse placement status (UNPLACED / IN PROCESS / OFFERED / PLACED) from the
 * real funnel — their offers and applications — rather than from a manual officer override. This is
 * the single source of truth for the {@code Student.placementStatus} column and is recomputed after
 * every funnel or offer transition (see {@code ApplicationService} / {@code OfferService}).
 *
 * <p>The "placed" definition mirrors analytics ({@code OfferRepository.countPlacedStudentsByCampusId}):
 * a student is PLACED once they have an ACCEPTED offer. It depends only on repositories, so it never
 * introduces a service-layer cycle with its callers.
 */
@Service
@RequiredArgsConstructor
public class PlacementStatusService {

    static final String PLACED = "PLACED";
    static final String OFFERED = "OFFERED";
    static final String IN_PROCESS = "IN PROCESS";
    static final String UNPLACED = "UNPLACED";

    /** Post-acceptance funnel stages: the student has secured and accepted a placement. */
    private static final Set<ApplicationStatus> PLACED_STAGES =
        Set.of(ApplicationStatus.ACCEPTED, ApplicationStatus.DOCS, ApplicationStatus.JOINED);

    /** Live, pre-offer funnel stages: the student is actively in a selection process. */
    private static final Set<ApplicationStatus> ACTIVE_STAGES = Set.of(
        ApplicationStatus.APPLIED, ApplicationStatus.SHORTLISTED, ApplicationStatus.ASSESSMENT,
        ApplicationStatus.INTERVIEW, ApplicationStatus.SELECTED);

    private final ApplicationRepository applicationRepository;
    private final OfferRepository offerRepository;
    private final StudentRepository studentRepository;

    /**
     * Recomputes and persists the student's placement status from their current offers/applications.
     * A no-op if the student does not exist or the derived value already matches what is stored.
     */
    @Transactional
    public void recompute(String studentId) {
        studentRepository.findById(studentId).ifPresent(student -> {
            String derived = derive(studentId);
            if (!derived.equals(student.getPlacementStatus())) {
                student.setPlacementStatus(derived);
                studentRepository.save(student);
            }
        });
    }

    /** Pure derivation of the coarse status from the funnel; does not touch the database for writes. */
    @Transactional(readOnly = true)
    public String derive(String studentId) {
        List<Offer> offers = offerRepository.findByStudentId(studentId);
        List<Application> applications = applicationRepository.findByStudentId(studentId);

        boolean placed = offers.stream().anyMatch(o -> o.getStatus() == OfferStatus.ACCEPTED)
            || applications.stream().anyMatch(a -> PLACED_STAGES.contains(a.getStatus()));
        if (placed) {
            return PLACED;
        }

        boolean offered = offers.stream().anyMatch(o -> o.getStatus() == OfferStatus.EXTENDED)
            || applications.stream().anyMatch(a -> a.getStatus() == ApplicationStatus.OFFER);
        if (offered) {
            return OFFERED;
        }

        boolean inProcess = applications.stream().anyMatch(a -> ACTIVE_STAGES.contains(a.getStatus()));
        return inProcess ? IN_PROCESS : UNPLACED;
    }
}
