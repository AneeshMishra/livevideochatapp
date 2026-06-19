package com.platform.broadcaster.exception;

import java.util.UUID;

public class BroadcasterNotFoundException extends RuntimeException {

    public BroadcasterNotFoundException(UUID id) {
        super("Broadcaster not found: " + id);
    }

    public BroadcasterNotFoundException(String hint) {
        super("Broadcaster not found: " + hint);
    }
}
