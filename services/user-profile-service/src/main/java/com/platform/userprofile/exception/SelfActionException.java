package com.platform.userprofile.exception;

public class SelfActionException extends RuntimeException {
    public SelfActionException(String action) {
        super("Cannot " + action + " yourself");
    }
}
