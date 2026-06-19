package com.platform.broadcaster.domain;

public enum GeoBlockType {
    /** Broadcaster is hidden from viewers in this country. */
    BLOCK,
    /** Allow-list mode: only viewers from these countries can see the broadcaster. */
    ALLOW
}
