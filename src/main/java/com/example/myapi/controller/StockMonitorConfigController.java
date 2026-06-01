package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.StockMonitorConfigDTO;
import com.example.myapi.entity.StockMonitorConfig;
import com.example.myapi.service.StockMonitorService;
import com.example.myapi.service.WeChatNotificationService;
import com.example.myapi.config.TushareConfig;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stock-monitor/config")
@RequiredArgsConstructor
public class StockMonitorConfigController {

    private final StockMonitorService monitorService;
    private final WeChatNotificationService notificationService;
    private final TushareConfig tushareConfig;

    @GetMapping
    public ApiResponse<StockMonitorConfigDTO.Response> getConfig() {
        StockMonitorConfig config = monitorService.getConfig();
        return ApiResponse.ok(toResponse(config));
    }

    @PutMapping("/xueqiu")
    public ApiResponse<StockMonitorConfigDTO.Response> updateXueqiuCookie(
            @Valid @RequestBody StockMonitorConfigDTO.XueqiuUpdateRequest request) {
        StockMonitorConfig config = monitorService.updateXueqiuCookie(request.getCookie());
        return ApiResponse.ok(toResponse(config), "雪球Cookie已更新");
    }

    @DeleteMapping("/xueqiu")
    public ApiResponse<StockMonitorConfigDTO.Response> clearXueqiuCookie() {
        StockMonitorConfig config = monitorService.clearXueqiuCookie();
        return ApiResponse.ok(toResponse(config), "雪球Cookie已清除");
    }

    @PutMapping("/wechat")
    public ApiResponse<StockMonitorConfigDTO.Response> updateWeChatConfig(
            @RequestBody StockMonitorConfigDTO.WechatUpdateRequest request) {
        StockMonitorConfig config = monitorService.updateWeChatConfig(
                request.getAppId(),
                request.getAppSecret(),
                request.getTemplateId(),
                request.getOpenId()
        );
        return ApiResponse.ok(toResponse(config), "微信配置已更新");
    }

    @DeleteMapping("/wechat")
    public ApiResponse<StockMonitorConfigDTO.Response> clearWeChatConfig() {
        StockMonitorConfig config = monitorService.clearWeChatConfig();
        return ApiResponse.ok(toResponse(config), "微信配置已清除");
    }

    @PostMapping("/test-notification")
    public ApiResponse<Void> sendTestNotification() {
        notificationService.sendTestNotification("测试标题", "这是一条测试消息");
        return ApiResponse.ok(null, "测试通知已发送");
    }

    @PutMapping("/datasource")
    public ApiResponse<StockMonitorConfigDTO.Response> updateDataSource(
            @Valid @RequestBody StockMonitorConfigDTO.DataSourceUpdateRequest request) {
        StockMonitorService.DataSource dataSource;
        try {
            dataSource = StockMonitorService.DataSource.valueOf(request.getDataSource().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid data source: " + request.getDataSource() + ". Must be XUEQIU or TUSHARE");
        }
        StockMonitorConfig config = monitorService.setDataSource(dataSource);
        return ApiResponse.ok(toResponse(config), "数据源已切换为: " + dataSource.name());
    }

    private StockMonitorConfigDTO.Response toResponse(StockMonitorConfig config) {
        return new StockMonitorConfigDTO.Response(
                config.getId(),
                config.getXueqiuConfigured(),
                config.getXueqiuCookieMasked(),
                config.getWechatConfigured(),
                config.getWechatAppId() != null && !config.getWechatAppId().isBlank(),
                config.getWechatAppSecret() != null && !config.getWechatAppSecret().isBlank(),
                config.getWechatTemplateId() != null && !config.getWechatTemplateId().isBlank(),
                config.getWechatOpenId() != null && !config.getWechatOpenId().isBlank(),
                config.getWechatAppSecretMasked(),
                config.getDataSource(),
                tushareConfig.getToken() != null && !tushareConfig.getToken().isBlank()
        );
    }
}
