package com.platform.broadcaster.domain;

public enum BroadcasterStatus {
    OFFLINE,
    ONLINE,     // in a public room
    PRIVATE,    // in a private 1-on-1 show
    GROUP,      // in a group/ticketed show
    AWAY
}
