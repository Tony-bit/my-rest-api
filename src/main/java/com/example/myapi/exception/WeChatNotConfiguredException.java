package com.example.myapi.exception;

/**
 * Exception thrown when WeChat notification configuration is missing or invalid.
 */
public class WeChatNotConfiguredException extends BusinessException {

    public WeChatNotConfiguredException(String message) {
        super(message, 400);
    }
}
