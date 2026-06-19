package com.platform.broadcaster.exception;

import java.util.UUID;

public class StudioNotFoundException extends RuntimeException {

    public StudioNotFoundException(UUID id) {
        super("Studio not found: " + id);
    }
}
