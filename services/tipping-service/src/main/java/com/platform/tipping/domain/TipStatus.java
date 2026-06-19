package com.platform.tipping.domain;

public enum TipStatus {
    PENDING,    // created, wallet transfer in-flight
    COMPLETED,  // wallet transfer succeeded; event published to Kafka
    FAILED      // wallet transfer rejected (insufficient funds, service error)
}
