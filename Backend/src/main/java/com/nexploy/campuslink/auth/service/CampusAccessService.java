package com.nexploy.campuslink.auth.service;

import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.entity.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CampusAccessService {

    public boolean canAccessCampus(UserAccount user, String campusId) {
        if (user == null || campusId == null || campusId.isBlank()) {
            return false;
        }

        // Only admins operate across campuses. Officers are scoped to their assigned campus,
        // like students and recruiters, so they fall through to the equality check below.
        if (user.getRole() == UserRole.ADMIN) {
            return true;
        }

        return campusId.equals(user.getCampusId());
    }

    public void requireAccess(UserAccount user, String campusId) {
        if (!canAccessCampus(user, campusId)) {
            throw new AccessDeniedException("Access denied for campus: " + campusId);
        }
    }

    public UserAccount currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserAccount user)) {
            throw new AccessDeniedException("Authentication required");
        }
        return user;
    }
}
