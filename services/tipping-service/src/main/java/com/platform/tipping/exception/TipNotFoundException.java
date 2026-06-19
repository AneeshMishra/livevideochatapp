package com.platform.tipping.exception;

import java.util.UUID;

public class TipNotFoundException extends RuntimeException {
    public TipNotFoundException(UUID tipId) {
        super("Tip not found: " + tipId);
    }
}
