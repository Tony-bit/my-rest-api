package com.example.myapi.service;

import com.example.myapi.entity.StockMonitorConfig;
import com.example.myapi.exception.WeChatApiException;
import com.example.myapi.exception.WeChatNotConfiguredException;
import com.example.myapi.repository.StockMonitorConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for sending WeChat template messages.
 * Caches access tokens for 2 hours to avoid repeated API calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatNotificationService {

    private static final String WECHAT_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String WECHAT_TEMPLATE_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send";

    private final StockMonitorConfigRepository configRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final Duration TOKEN_CACHE_DURATION = Duration.ofHours(2);

    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValid() {
            return accessToken != null && Instant.now().isBefore(expiresAt.minus(Duration.ofMinutes(5)));
        }
    }

    /**
     * Sends a test notification.
     *
     * @param title Title of the notification
     * @param content Content of the notification
     * @throws WeChatNotConfiguredException if WeChat config is missing
     * @throws WeChatApiException if API call fails
     */
    public void sendTestNotification(String title, String content) {
        StockMonitorConfig config = configRepository.getConfig();
        validateConfig(config);

        String accessToken = getAccessToken(config);

        Map<String, Object> data = Map.of(
                "first", Map.of("value", title, "color", "#173177"),
                "keyword1", Map.of("value", "测试消息", "color", "#173177"),
                "keyword2", Map.of("value", content, "color", "#173177"),
                "keyword3", Map.of("value", java.time.LocalDateTime.now().toString(), "color", "#173177"),
                "remark", Map.of("value", "这是一条测试消息", "color", "#888888")
        );

        sendTemplateMessage(accessToken, config.getWechatOpenId(), config.getWechatTemplateId(), data);
    }

    /**
     * Sends a stock signal notification.
     *
     * @param signalType Signal type: "BUY" or "SELL"
     * @param stockCode Stock code
     * @param stockName Stock name
     * @param closePrice Close price
     * @param jValue J value
     * @param macdValue MACD value
     * @param summary Signal summary/reason
     * @throws WeChatNotConfiguredException if WeChat config is missing
     * @throws WeChatApiException if API call fails
     */
    public void sendSignalNotification(String signalType, String stockCode, String stockName,
                                       String closePrice, String jValue, String macdValue, String summary) {
        StockMonitorConfig config = configRepository.getConfig();
        validateConfig(config);

        String accessToken = getAccessToken(config);

        String title = "📈 " + ("BUY".equals(signalType) ? "买入信号触发" : "📉 卖出信号触发");
        String signalEmoji = "BUY".equals(signalType) ? "📈" : "📉";

        Map<String, Object> data = Map.of(
                "first", Map.of("value", title, "color", "#173177"),
                "keyword1", Map.of("value", stockCode + (stockName != null ? " " + stockName : ""), "color", "#173177"),
                "keyword2", Map.of("value", signalType, "color", "#173177"),
                "keyword3", Map.of("value", java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), "color", "#173177"),
                "remark", Map.of("value", String.format("J值: %s, MACD: %s", jValue, macdValue), "color", "#888888")
        );

        sendTemplateMessage(accessToken, config.getWechatOpenId(), config.getWechatTemplateId(), data);
        log.info("Sent signal notification: stock={}, type={}, code={}", stockCode, signalType, maskCode(stockCode));
    }

    private void validateConfig(StockMonitorConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.getWechatConfigured())) {
            throw new WeChatNotConfiguredException("微信配置未完成，请检查AppID、AppSecret、模板ID和OpenID");
        }
        if (config.getWechatAppId() == null || config.getWechatAppId().isBlank()) {
            throw new WeChatNotConfiguredException("微信AppID未配置");
        }
        if (config.getWechatAppSecret() == null || config.getWechatAppSecret().isBlank()) {
            throw new WeChatNotConfiguredException("微信AppSecret未配置");
        }
        if (config.getWechatTemplateId() == null || config.getWechatTemplateId().isBlank()) {
            throw new WeChatNotConfiguredException("微信模板ID未配置");
        }
        if (config.getWechatOpenId() == null || config.getWechatOpenId().isBlank()) {
            throw new WeChatNotConfiguredException("微信OpenID未配置");
        }
    }

    private String getAccessToken(StockMonitorConfig config) {
        String cacheKey = config.getWechatAppId();

        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            return cached.accessToken();
        }

        String url = String.format("%s?grant_type=client_credential&appid=%s&secret=%s",
                WECHAT_TOKEN_URL, config.getWechatAppId(), config.getWechatAppSecret());

        log.debug("Getting WeChat access token for appid={}", maskCode(config.getWechatAppId()));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new WeChatApiException("获取access_token失败: HTTP " + response.statusCode(), -1);
            }

            JsonNode json = objectMapper.readTree(response.body());

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                int errcode = json.get("errcode").asInt();
                String errmsg = json.has("errmsg") ? json.get("errmsg").asText() : "unknown";
                throw new WeChatApiException(String.format("获取access_token失败: errcode=%d, errmsg=%s", errcode, errmsg), errcode);
            }

            String accessToken = json.get("access_token").asText();
            int expiresIn = json.has("expires_in") ? json.get("expires_in").asInt() : 7200;

            tokenCache.put(cacheKey, new CachedToken(accessToken, Instant.now().plusSeconds(expiresIn)));
            log.info("Obtained new WeChat access token, expires in {} seconds", expiresIn);

            return accessToken;

        } catch (WeChatApiException e) {
            throw e;
        } catch (Exception e) {
            throw new WeChatApiException("获取access_token异常: " + e.getMessage(), -1);
        }
    }

    private void sendTemplateMessage(String accessToken, String openId, String templateId, Map<String, Object> data) {
        String url = WECHAT_TEMPLATE_URL + "?access_token=" + accessToken;

        Map<String, Object> body = Map.of(
                "touser", openId,
                "template_id", templateId,
                "data", data
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new WeChatApiException("发送模板消息失败: HTTP " + response.statusCode(), -1);
            }

            JsonNode json = objectMapper.readTree(response.body());

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                int errcode = json.get("errcode").asInt();
                String errmsg = json.has("errmsg") ? json.get("errmsg").asText() : "unknown";
                throw new WeChatApiException(String.format("发送模板消息失败: errcode=%d, errmsg=%s", errcode, errmsg), errcode);
            }

            log.info("Template message sent successfully, msgid={}", json.has("msgid") ? json.get("msgid").asText() : "N/A");

        } catch (WeChatApiException e) {
            throw e;
        } catch (Exception e) {
            throw new WeChatApiException("发送模板消息异常: " + e.getMessage(), -1);
        }
    }

    private String maskCode(String code) {
        if (code == null || code.length() < 6) {
            return "****";
        }
        return code.substring(0, 3) + "****";
    }

    /**
     * Clears the access token cache. Useful for testing or forced refresh.
     */
    public void clearTokenCache() {
        tokenCache.clear();
    }
}
