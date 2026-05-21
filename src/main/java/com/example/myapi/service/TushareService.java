package com.example.myapi.service;

import com.example.myapi.config.TushareConfig;
import com.example.myapi.entity.ConditionType;
import com.example.myapi.entity.PlanCondition;
import com.example.myapi.entity.TradeDirection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TushareService {

    private final TushareConfig tushareConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<KLineData> getDailyKLine(String stockCode, LocalDate date) {
        return getDailyKLine(stockCode, date, false);
    }

    public Optional<KLineData> getDailyKLine(String stockCode, LocalDate date, boolean usePreFQ) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ts_code", stockCode);
            params.put("trade_date", date.toString().replace("-", ""));
            if (usePreFQ) {
                params.put("fq", "pre");
            }

            JsonNode result = callApi("daily", params);
            if (result != null && result.has("items") && result.get("items").size() > 0) {
                JsonNode item = result.get("items").get(0);
                return parseKLine(stockCode, item, date);
            }
        } catch (Exception e) {
            log.error("Failed to fetch daily K-line for {} on {}", stockCode, date, e);
        }
        return Optional.empty();
    }

    public BigDecimal calculateMA(String stockCode, int period, LocalDate date) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ts_code", stockCode);
            params.put("trade_date", date.toString().replace("-", ""));
            params.put("fq", "pre");

            JsonNode result = callApi("daily", params);
            if (result == null || !result.has("items")) {
                return null;
            }

            LinkedList<BigDecimal> closes = new LinkedList<>();
            for (JsonNode item : result.get("items")) {
                if (item.size() >= 6) {
                    closes.addLast(new BigDecimal(item.get(5).asText()));
                }
                if (closes.size() >= period) break;
            }

            if (closes.size() < period) {
                LocalDate prevDate = date.minusDays(1);
                while (closes.size() < period) {
                    Optional<KLineData> kData = getDailyKLine(stockCode, prevDate, true);
                    if (kData.isPresent()) {
                        closes.addFirst(kData.get().close);
                    } else {
                        prevDate = prevDate.minusDays(1);
                    }
                    if (prevDate.isBefore(date.minusYears(1))) break;
                }
            }

            if (closes.size() < period) return null;

            List<BigDecimal> lastN = closes.subList(closes.size() - period, closes.size());
            BigDecimal sum = lastN.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.error("Failed to calculate MA{} for {} on {}", period, stockCode, date, e);
            return null;
        }
    }

    public boolean evaluateCondition(PlanCondition condition, KLineData kData, BigDecimal maValue) {
        ConditionType type = condition.getConditionType();
        TradeDirection dir = condition.getDirection();
        BigDecimal close = kData.close;

        if (type == ConditionType.PRICE) {
            BigDecimal target = condition.getTargetPrice();
            if (dir == TradeDirection.BUY) return close.compareTo(target) >= 0;
            if (dir == TradeDirection.SELL) return close.compareTo(target) <= 0;
        } else if (type == ConditionType.MA) {
            if (maValue == null) return false;
            if (dir == TradeDirection.BUY) return close.compareTo(maValue) >= 0;
            if (dir == TradeDirection.SELL) return close.compareTo(maValue) <= 0;
        }
        return false;
    }

    public boolean isTradingDay(LocalDate date) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("trade_date", date.toString().replace("-", ""));
            JsonNode result = callApi("trade_cal", params);
            if (result == null || !result.has("items") || result.get("items").size() == 0) {
                return true;
            }
            JsonNode fieldsNode = result.get("fields");
            int isOpenIndex = -1;
            if (fieldsNode != null) {
                for (int i = 0; i < fieldsNode.size(); i++) {
                    if ("is_open".equals(fieldsNode.get(i).asText())) {
                        isOpenIndex = i;
                        break;
                    }
                }
            }
            if (isOpenIndex == -1) isOpenIndex = 1;
            return "1".equals(result.get("items").get(0).get(isOpenIndex).asText());
        } catch (Exception e) {
            log.warn("Failed to check trading day for {}, assuming it is", date);
            return true;
        }
    }

    private Optional<KLineData> parseKLine(String stockCode, JsonNode item, LocalDate date) {
        try {
            BigDecimal close = new BigDecimal(item.get(5).asText());
            BigDecimal high = new BigDecimal(item.get(3).asText());
            BigDecimal low = new BigDecimal(item.get(4).asText());
            BigDecimal open = new BigDecimal(item.get(2).asText());
            double vol = item.get(6).asDouble();
            return Optional.of(new KLineData(stockCode, date, open, high, low, close, vol));
        } catch (Exception e) {
            log.error("Failed to parse K-line item for {}", stockCode, e);
            return Optional.empty();
        }
    }

    private JsonNode callApi(String apiName, Map<String, Object> params) throws Exception {
        String token = tushareConfig.getToken();
        if (token == null || token.isBlank()) {
            log.warn("Tushare token not configured");
            return null;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("api_name", apiName);
        body.put("token", token);
        body.put("params", params);
        body.put("fields", "");

        String json = objectMapper.writeValueAsString(body);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tushareConfig.getBaseUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(java.time.Duration.ofMillis(tushareConfig.getTimeout()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.error("Tushare API error: status={} body={}", response.statusCode(), response.body());
            return null;
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (root.has("code") && root.get("code").asInt() != 0) {
            log.error("Tushare API error: code={} msg={}", root.get("code"), root.get("msg"));
            return null;
        }
        return root.get("data");
    }

    public static class KLineData {
        public final String stockCode;
        public final LocalDate date;
        public final BigDecimal open;
        public final BigDecimal high;
        public final BigDecimal low;
        public final BigDecimal close;
        public final double volume;

        public KLineData(String stockCode, LocalDate date, BigDecimal open,
                         BigDecimal high, BigDecimal low, BigDecimal close, double volume) {
            this.stockCode = stockCode;
            this.date = date;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
        }
    }
}
