package com.example.myapi.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

public class StockMonitorConfigDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Boolean xueqiuConfigured;
        private String xueqiuCookieMasked;
        private Boolean wechatConfigured;
        private Boolean wechatAppIdConfigured;
        private Boolean wechatAppSecretConfigured;
        private Boolean wechatTemplateIdConfigured;
        private Boolean wechatOpenIdConfigured;
        private String wechatAppSecretMasked;
        private String dataSource;
        private Boolean tushareConfigured;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class XueqiuUpdateRequest {
        @NotBlank(message = "Cookie is required")
        private String cookie;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WechatUpdateRequest {
        private String appId;
        private String appSecret;
        private String templateId;
        private String openId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSourceUpdateRequest {
        @NotBlank(message = "Data source is required")
        private String dataSource; // "XUEQIU" or "TUSHARE"
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestNotificationRequest {
        private String type; // "test" or "signal"
        private String stockCode;
        private String stockName;
        private String signalType;
        private String closePrice;
        private String jValue;
        private String macdValue;
        private String summary;
    }
}
