package com.example.myapi.exception;

/**
 * Exception thrown when Xueqiu API call fails.
 */
public class XueqiuApiException extends BusinessException {

    public XueqiuApiException(String message) {
        super(message, 502);
    }

    public XueqiuApiException(String message, Throwable cause) {
        super(message + ": " + cause.getMessage(), 502);
    }
}
