package com.platform.payments.domain;

public enum PaymentProvider {
    CCBILL,   // primary high-risk acquirer
    EPOCH,    // alternative acquirer
    SEGPAY,   // alternative acquirer
    VEROTEL,  // EU-focused acquirer
    VENDO     // EU-focused acquirer
}
