package com.platform.payments.domain;

public enum OrderStatus {
    PENDING,      // order created, awaiting user redirect to payment page
    PROCESSING,   // user has been redirected to provider payment page
    COMPLETED,    // payment confirmed via provider webhook
    FAILED,       // payment declined or timed out
    REFUNDED,     // refunded to customer
    CHARGEBACK    // customer filed a chargeback with their bank
}
