package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Singleton configuration for Xueqiu cookie and WeChat test account.
 */
@Entity
@Table(name = "stock_monitor_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMonitorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "xueqiu_cookie", columnDefinition = "TEXT")
    private String xueqiuCookie;

    @Column(name = "xueqiu_configured", nullable = false)
    @Builder.Default
    private Boolean xueqiuConfigured = false;

    @Column(name = "wechat_app_id", length = 50)
    private String wechatAppId;

    @Column(name = "wechat_app_secret", length = 100)
    private String wechatAppSecret;

    @Column(name = "wechat_template_id", length = 100)
    private String wechatTemplateId;

    @Column(name = "wechat_open_id", length = 100)
    private String wechatOpenId;

    @Column(name = "wechat_configured", nullable = false)
    @Builder.Default
    private Boolean wechatConfigured = false;

    /**
     * Data source for K-line data: XUEQIU or TUSHARE
     */
    @Column(name = "data_source", length = 20)
    @Builder.Default
    private String dataSource = "XUEQIU";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Returns a masked preview of the cookie for display purposes.
     */
    public String getXueqiuCookieMasked() {
        if (xueqiuCookie == null || xueqiuCookie.length() < 10) {
            return "未配置";
        }
        return xueqiuCookie.substring(0, 4) + "****" + xueqiuCookie.substring(xueqiuCookie.length() - 4);
    }

    /**
     * Returns masked WeChat AppSecret for display purposes.
     */
    public String getWechatAppSecretMasked() {
        if (wechatAppSecret == null || wechatAppSecret.length() < 6) {
            return "未配置";
        }
        return wechatAppSecret.substring(0, 3) + "****" + wechatAppSecret.substring(wechatAppSecret.length() - 3);
    }
}
