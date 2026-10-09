package com.nexploy.campuslink.auth.dto;

import com.nexploy.campuslink.auth.entity.UserAccount;

/**
 * The authenticated principal's own identity, derived entirely from the JWT-backed
 * {@link UserAccount}. There is no display name or email on a user account (those live only on
 * the Student profile), so this deliberately exposes only what actually exists: the login
 * username, the role, and the home campus. It never fabricates a name.
 */
public record CurrentUserResponse(String username, String role, String campusId) {

    public static CurrentUserResponse from(UserAccount account) {
        return new CurrentUserResponse(
            account.getUsername(),
            account.getRole().name(),
            account.getCampusId()
        );
    }
}
