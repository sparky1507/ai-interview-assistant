package com.harsh.interviewassistant.common.exception;

/** The request is valid but clashes with existing state (e.g. email already registered) -> 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
