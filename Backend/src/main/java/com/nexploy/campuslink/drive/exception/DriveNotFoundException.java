package com.nexploy.campuslink.drive.exception;

import com.nexploy.campuslink.drive.entity.Drive;

public class DriveNotFoundException extends RuntimeException {

    public DriveNotFoundException(String driveId) {
        super("Drive not found: " + driveId);
    }
}

