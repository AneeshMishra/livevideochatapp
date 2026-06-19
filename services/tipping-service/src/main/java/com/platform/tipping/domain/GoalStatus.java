package com.platform.tipping.domain;

public enum GoalStatus {
    ACTIVE,     // accepting contributions
    COMPLETED,  // target reached
    CANCELLED   // broadcaster cancelled early
}
