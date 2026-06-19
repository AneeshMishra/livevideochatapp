package com.platform.wallet.domain;

public enum TransactionType {
    // Viewer buys tokens with real money (payment service triggers this)
    PURCHASE,

    // Viewer sends a tip — debit from viewer wallet
    TIP_SENT,

    // Broadcaster receives tip net of platform fee
    TIP_RECEIVED,

    // Platform takes its cut from a tip
    PLATFORM_FEE,

    // Per-minute private show billing — debit from viewer
    PRIVATE_SHOW_DEBIT,

    // Per-minute private show credit — to broadcaster (after fee)
    PRIVATE_SHOW_CREDIT,

    // Viewer buys a piece of content (VOD, photo set)
    CONTENT_PURCHASE,

    // Broadcaster receives content sale revenue
    CONTENT_SALE,

    // Fan club / subscription debit from viewer
    SUBSCRIPTION_DEBIT,

    // Broadcaster receives subscription revenue
    SUBSCRIPTION_CREDIT,

    // Refund tokens back to viewer (e.g. disputed charge)
    REFUND,

    // Admin-issued balance correction
    ADJUSTMENT
}
