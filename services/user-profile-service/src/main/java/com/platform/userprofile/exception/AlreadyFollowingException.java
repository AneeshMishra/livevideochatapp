package com.platform.userprofile.exception;

public class AlreadyFollowingException extends RuntimeException {
    public AlreadyFollowingException() {
        super("Already following this broadcaster");
    }
}
