package com.nexploy.campuslink.student.exception;

/**
 * The directory record is already claimed by a registered account (email == username), so the
 * requested mutation — renaming the email — would break the identity link between user_accounts
 * and students. Maps to 409.
 */
public class DirectoryRecordLinkedException extends RuntimeException {
    public DirectoryRecordLinkedException(String studentId) {
        super("This directory record is linked to a registered account; its email can no longer be changed: "
            + studentId);
    }
}
