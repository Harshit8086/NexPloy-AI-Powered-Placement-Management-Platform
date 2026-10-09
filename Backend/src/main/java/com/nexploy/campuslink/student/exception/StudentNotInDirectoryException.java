package com.nexploy.campuslink.student.exception;

/**
 * Registration gating (V60): the typed email is not present in the campus's student directory, so
 * no account can be created. The registrant must get added to the directory by their Placement
 * Officer first. Maps to 400 — a client-visible, actionable refusal rather than a server fault.
 */
public class StudentNotInDirectoryException extends RuntimeException {
    public StudentNotInDirectoryException() {
        super("You are not in your campus directory. Contact your Placement Officer.");
    }
}
