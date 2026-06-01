package com.example.myapi.exception;

/**
 * Exception thrown when WeChat API call fails.
 */
public class WeChatApiException extends BusinessException {

    private final int wechatErrorCode;

    public WeChatApiException(String message, int wechatErrorCode) {
        super(message, 502);
        this.wechatErrorCode = wechatErrorCode;
    }

    public int getWechatErrorCode() {
        return wechatErrorCode;
    }
}
