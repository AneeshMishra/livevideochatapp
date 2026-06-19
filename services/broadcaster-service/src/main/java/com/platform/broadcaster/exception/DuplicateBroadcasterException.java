package com.platform.broadcaster.exception;

import java.util.UUID;

public class DuplicateBroadcasterException extends RuntimeException {

    public DuplicateBroadcasterException(UUID userId) {
        super("A broadcaster account already exists for userId=" + userId);
    }
}
