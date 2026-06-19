package com.platform.tipping.exception;

import java.util.UUID;

public class TipGoalNotFoundException extends RuntimeException {
    public TipGoalNotFoundException(UUID goalId) {
        super("Tip goal not found: " + goalId);
    }
}
