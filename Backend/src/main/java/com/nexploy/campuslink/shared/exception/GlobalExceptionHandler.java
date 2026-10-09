package com.nexploy.campuslink.shared.exception;

import com.nexploy.campuslink.ai.exception.PromptInjectionException;
import com.nexploy.campuslink.aptitude.exception.AptitudeAttemptNotFoundException;
import com.nexploy.campuslink.aptitude.exception.AptitudeQuestionNotFoundException;
import com.nexploy.campuslink.aptitude.exception.AptitudeTestNotFoundException;
import com.nexploy.campuslink.aptitude.exception.InsufficientAptitudeBankException;
import com.nexploy.campuslink.aptitude.exception.InvalidAptitudeAttemptStateException;
import com.nexploy.campuslink.application.exception.AlreadyAppliedException;
import com.nexploy.campuslink.application.exception.DriveClosedException;
import com.nexploy.campuslink.application.exception.InvalidApplicationTransitionException;
import com.nexploy.campuslink.application.exception.NotEligibleException;
import com.nexploy.campuslink.auth.exception.InvalidRefreshTokenException;
import com.nexploy.campuslink.auth.exception.UsernameAlreadyExistsException;
import com.nexploy.campuslink.campus.exception.CampusNotFoundException;
import com.nexploy.campuslink.document.exception.DocumentFileMissingException;
import com.nexploy.campuslink.document.exception.DocumentNotFoundException;
import com.nexploy.campuslink.document.exception.DocumentNotReviewableException;
import com.nexploy.campuslink.document.exception.UnsupportedContentTypeException;
import com.nexploy.campuslink.drive.exception.DriveNotFoundException;
import com.nexploy.campuslink.drive.exception.InvalidDriveTransitionException;
import com.nexploy.campuslink.interview.exception.InterviewNotFoundException;
import com.nexploy.campuslink.interview.exception.InvalidInterviewTransitionException;
import com.nexploy.campuslink.interview.exception.InvalidMockInterviewStateException;
import com.nexploy.campuslink.interview.exception.MockInterviewNotFoundException;
import com.nexploy.campuslink.mentor.exception.StudentNotAssignedException;
import com.nexploy.campuslink.notification.exception.NotificationNotFoundException;
import com.nexploy.campuslink.notification.exception.NotificationNotRetryableException;
import com.nexploy.campuslink.offer.exception.InvalidOfferTransitionException;
import com.nexploy.campuslink.offer.exception.DocumentsNotVerifiedException;
import com.nexploy.campuslink.offer.exception.OfferNotFoundException;
import com.nexploy.campuslink.placement.exception.InvalidCycleTransitionException;
import com.nexploy.campuslink.placement.exception.PlacementCycleNotFoundException;
import com.nexploy.campuslink.recruiter.exception.RecruiterNotFoundException;
import com.nexploy.campuslink.risk.exception.InterventionPlanNotFoundException;
import com.nexploy.campuslink.risk.exception.InvalidInterventionTransitionException;
import com.nexploy.campuslink.schedule.exception.InvalidOptimizationRunTransitionException;
import com.nexploy.campuslink.schedule.exception.OptimizationRunNotFoundException;
import com.nexploy.campuslink.schedule.exception.PanelNotFoundException;
import com.nexploy.campuslink.schedule.exception.ScheduleConflictNotFoundException;
import com.nexploy.campuslink.schedule.exception.ScheduleEventNotFoundException;
import com.nexploy.campuslink.schedule.exception.StaleOptimizationRunException;
import com.nexploy.campuslink.schedule.exception.VenueNotFoundException;
import com.nexploy.campuslink.shared.dto.ApiError;
import com.nexploy.campuslink.student.exception.ResumeAnalysisNotFoundException;
import com.nexploy.campuslink.student.exception.CertificateNotFoundException;
import com.nexploy.campuslink.student.exception.DirectoryRecordLinkedException;
import com.nexploy.campuslink.student.exception.StudentAlreadyExistsException;
import com.nexploy.campuslink.student.exception.StudentNotFoundException;
import com.nexploy.campuslink.student.exception.StudentNotInDirectoryException;
import com.nexploy.campuslink.student.exception.StudentSkillNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
        StudentNotFoundException.class,
        DriveNotFoundException.class,
        OptimizationRunNotFoundException.class,
        InterviewNotFoundException.class,
        MockInterviewNotFoundException.class,
        OfferNotFoundException.class,
        DocumentNotFoundException.class,
        DocumentFileMissingException.class,
        NotificationNotFoundException.class,
        ScheduleEventNotFoundException.class,
        ScheduleConflictNotFoundException.class,
        InterventionPlanNotFoundException.class,
        StudentSkillNotFoundException.class,
        CertificateNotFoundException.class,
        ResumeAnalysisNotFoundException.class,
        PlacementCycleNotFoundException.class,
        CampusNotFoundException.class,
        RecruiterNotFoundException.class,
        VenueNotFoundException.class,
        PanelNotFoundException.class,
        AptitudeTestNotFoundException.class,
        AptitudeAttemptNotFoundException.class,
        AptitudeQuestionNotFoundException.class
    })
    public ResponseEntity<ApiError> handleNotFound(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        StudentNotInDirectoryException.class,
        UnsupportedContentTypeException.class,
        PromptInjectionException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler({AccessDeniedException.class, StudentNotAssignedException.class})
    public ResponseEntity<ApiError> handleForbidden(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return response(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message, request);
    }

    /**
     * A required request parameter or multipart part was absent (e.g. a multipart create missing a
     * form field or the file). That is a malformed client request — 400, not a 500 server fault.
     */
    @ExceptionHandler({
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class,
        org.springframework.web.bind.ServletRequestBindingException.class
    })
    public ResponseEntity<ApiError> handleMissingRequestValue(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> handleUnauthorized(InvalidRefreshTokenException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler({
        StaleOptimizationRunException.class,
        InvalidOptimizationRunTransitionException.class,
        InvalidInterventionTransitionException.class,
        InvalidOfferTransitionException.class,
        DocumentsNotVerifiedException.class,
        InvalidDriveTransitionException.class,
        InvalidInterviewTransitionException.class,
        InvalidMockInterviewStateException.class,
        InvalidApplicationTransitionException.class,
        InvalidCycleTransitionException.class,
        DocumentNotReviewableException.class,
        NotificationNotRetryableException.class,
        UsernameAlreadyExistsException.class,
        StudentAlreadyExistsException.class,
        DirectoryRecordLinkedException.class,
        AlreadyAppliedException.class,
        DriveClosedException.class,
        NotEligibleException.class,
        InvalidAptitudeAttemptStateException.class,
        InsufficientAptitudeBankException.class
    })
    public ResponseEntity<ApiError> handleConflict(RuntimeException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    /**
     * Defense-in-depth for the unique (campus_id, lower(email)) indexes added in V47 (audit plan
     * G3), plus any other DB-level integrity constraint. A constraint violation is a client-caused
     * conflict, not a server fault, so it maps to 409 rather than falling through to 500. When the
     * offending constraint is one of the campus-email indexes we surface a clear, actionable
     * message; other violations get a generic conflict message.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        Throwable cause = exception.getMostSpecificCause();
        String detail = cause != null && cause.getMessage() != null
            ? cause.getMessage().toLowerCase(Locale.ROOT)
            : "";
        // The generic 409 message hides which constraint fired. Log the most-specific cause (e.g.
        // the failing FK/unique constraint name) so a conflict is diagnosable without a stack trace.
        log.warn("Data integrity violation on {} {} -> 409. constraint/cause={}",
            request.getMethod(), request.getRequestURI(),
            cause != null ? cause.getMessage() : exception.getMessage());
        String message = detail.contains("uq_students_campus_email") || detail.contains("uq_recruiters_campus_email")
            ? "An account with this email already exists for this campus."
            : "The request conflicts with existing data.";
        return response(HttpStatus.CONFLICT, message, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        return response(
            status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR,
            exception.getReason(),
            request
        );
    }

    /**
     * Unmatched API paths (a stray trailing slash, an empty segment, a wrong prefix) are client
     * errors, not server faults. Without this handler they fall through to the 500 catch-all below.
     */
    @ExceptionHandler({
        org.springframework.web.servlet.NoHandlerFoundException.class,
        org.springframework.web.servlet.resource.NoResourceFoundException.class
    })
    public ResponseEntity<ApiError> handleUnmatchedPath(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage(), request);
    }

    private ResponseEntity<ApiError> response(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            request.getRequestURI()
        ));
    }
}

