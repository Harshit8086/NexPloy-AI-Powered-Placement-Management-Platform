package com.nexploy.campuslink.auth.exception;

/**
 * Raised when registration is attempted with a username that is already taken. Maps to HTTP 409.
 */
public class UsernameAlreadyExistsException extends RuntimeException {

    public UsernameAlreadyExistsException(String username) {
        super("Username already taken: " + username);
    }
}
