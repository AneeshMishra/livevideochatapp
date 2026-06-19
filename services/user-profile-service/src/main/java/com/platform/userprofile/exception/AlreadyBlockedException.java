package com.platform.userprofile.exception;

public class AlreadyBlockedException extends RuntimeException {
    public AlreadyBlockedException() {
        super("User is already blocked");
    }
}
