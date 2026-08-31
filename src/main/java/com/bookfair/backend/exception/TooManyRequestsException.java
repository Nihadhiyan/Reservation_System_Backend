package com.bookfair.backend.exception;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends BaseException {
    
    public TooManyRequestsException(String message, ErrorCode errorCode) {
        super(message, errorCode, HttpStatus.TOO_MANY_REQUESTS);
    }
    
    public TooManyRequestsException(String message, Throwable cause, ErrorCode errorCode) {
        super(message, cause, errorCode, HttpStatus.TOO_MANY_REQUESTS);
    }
    
}
