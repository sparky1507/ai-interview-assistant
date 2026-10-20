package com.harsh.interviewassistant.common.exception;

/** Uploaded content is of a type the API doesn't accept -> 415. */
public class UnsupportedMediaTypeException extends RuntimeException {

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
