package com.example.myapi.service;

import com.example.myapi.config.TushareConfig;
import com.example.myapi.entity.ConditionType;
import com.example.myapi.entity.PlanCondition;
import com.example.myapi.entity.PlanType;
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

    /**
     * 标准化股票代码格式，转换为 Tushare 所需格式
     * 规则：6开头加.SH，3/0开头加.SZ，已有后缀的不处理
     */
    public String normalizeStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return stockCode;
        }
        // 已有后缀的直接返回
        if (stockCode.contains(".")) {
            return stockCode;
        }
        // 6 开头：上海主板
        if (stockCode.startsWith("6")) {
            return stockCode + ".SH";
        }
        // 3 或 0 开头：深圳
        if (stockCode.startsWith("3") || stockCode.startsWith("0")) {
            return stockCode + ".SZ";
        }
        return stockCode;
    }

    /**
     * 获取股票名称
     */
    public Optional<String> getStockName(String stockCode) {
        try {
            String normalizedCode = normalizeStockCode(stockCode);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ts_code", normalizedCode);

            JsonNode result = callApi("stock_basic", params);
            if (result != null && result.has("items") && result.get("items").size() > 0) {
                JsonNode fieldsNode = result.get("fields");
                int nameIndex = -1;
                for (int i = 0; i < fieldsNode.size(); i++) {
                    if ("name".equals(fieldsNode.get(i).asText())) {
                        nameIndex = i;
                        break;
                    }
                }
                if (nameIndex >= 0) {
                    JsonNode item = result.get("items").get(0);
                    return Optional.ofNullable(item.get(nameIndex)).map(JsonNode::asText);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get stock name for {}: {}", stockCode, e.getMessage());
        }
        return Optional.empty();
    }

    public Optional<KLineData> getDailyKLine(String stockCode, LocalDate date) {
        return getDailyKLine(stockCode, date, false);
    }

    public Optional<KLineData> getDailyKLine(String stockCode, LocalDate date, boolean usePreFQ) {
        try {
            String normalizedCode = normalizeStockCode(stockCode);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ts_code", normalizedCode);
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
            String normalizedCode = normalizeStockCode(stockCode);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ts_code", normalizedCode);
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

    private static final BigDecimal TOUCH_THRESHOLD = new BigDecimal("0.003"); // 0.3%

    public boolean evaluateCondition(PlanCondition condition, PlanType planType, KLineData kData, BigDecimal maValue) {
        ConditionType type = condition.getConditionType();
        BigDecimal target = condition.getTargetPrice();

        if (type == ConditionType.PRICE) {
            BigDecimal high = kData.high;
            BigDecimal low = kData.low;
            // 只要当天的价格区间包含目标价就触发
            return target.compareTo(low) >= 0 && target.compareTo(high) <= 0;
        } else if (type == ConditionType.MA) {
            if (maValue == null) return false;
            BigDecimal close = kData.close;
            BigDecimal diff = close.subtract(maValue).abs();
            BigDecimal ratio = diff.divide(close, 6, RoundingMode.HALF_UP);
            return ratio.compareTo(TOUCH_THRESHOLD) <= 0;
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
        log.info("Tushare request: api={}, params={}", apiName, params);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tushareConfig.getBaseUrl()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(java.time.Duration.ofMillis(tushareConfig.getTimeout()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Tushare response: api={}, status={}, body={}", apiName, response.statusCode(), response.body());

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

    /**
     * Result of fetching K-line data with indicators.
     */
    public record KLineResult(
            List<KLineRow> rows,
            boolean suspended
    ) {}

    /**
     * A single row of K-line data with KDJ/MACD indicators.
     */
    public record KLineRow(
            long timestamp,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            BigDecimal dif,
            BigDecimal dea,
            BigDecimal macd,
            BigDecimal kdjk,
            BigDecimal kdjd,
            BigDecimal kdjj
    ) implements StockSignalService.KLineRowInput {
        public LocalDate toLocalDate() {
            return java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                    .toLocalDate();
        }

        @Override
        public BigDecimal getJ() {
            return kdjj;
        }

        @Override
        public BigDecimal getMacd() {
            return macd;
        }
    }

    /**
     * Fetches K-line data from Tushare with calculated KDJ/MACD indicators.
     *
     * @param stockCode 6-digit A-share stock code
     * @param count Number of rows to fetch
     * @return KLineResult containing rows sorted by date descending, or suspended flag
     */
    public KLineResult fetchKLineData(String stockCode, int count) {
        try {
            String normalizedCode = normalizeStockCode(stockCode);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ts_code", normalizedCode);
            params.put("start_date", LocalDate.now().minusYears(1).toString().replace("-", ""));
            params.put("end_date", LocalDate.now().toString().replace("-", ""));
            params.put("fq", "pre");

            JsonNode result = callApi("daily", params);
            if (result == null || !result.has("items") || result.get("items").size() == 0) {
                return new KLineResult(List.of(), false);
            }

            JsonNode fieldsNode = result.get("fields");
            Map<String, Integer> fieldIndex = new HashMap<>();
            if (fieldsNode != null) {
                for (int i = 0; i < fieldsNode.size(); i++) {
                    fieldIndex.put(fieldsNode.get(i).asText(), i);
                }
            }

            List<Object[]> rawData = new ArrayList<>();
            for (JsonNode item : result.get("items")) {
                Object[] row = new Object[item.size()];
                for (int i = 0; i < item.size(); i++) {
                    JsonNode node = item.get(i);
                    if (node.isNull()) {
                        row[i] = null;
                    } else if (node.isNumber()) {
                        row[i] = new BigDecimal(node.asText());
                    } else {
                        row[i] = node.asText(); // strings like ts_code and trade_date
                    }
                }
                rawData.add(row);
            }

            // Reverse to chronological order
            Collections.reverse(rawData);

            // Field indices (fixed positions in daily API):
            // 0: ts_code (String), 1: trade_date (String yyyyMMdd), 2: open, 3: high, 4: low, 5: close, 6: pre_close, 7: change, 8: pct_chg, 9: vol, 10: amount
            int tradeDateIdx = 1;
            int openIdx = 2;
            int highIdx = 3;
            int lowIdx = 4;
            int closeIdx = 5;
            int volIdx = 9;

            // Extract OHLCV data
            List<Object[]> ohlcvData = new ArrayList<>();
            List<LocalDate> dates = new ArrayList<>();
            for (Object[] row : rawData) {
                try {
                    // trade_date format: "20260529" -> parse as yyyyMMdd
                    String dateStr = (String) row[tradeDateIdx];
                    LocalDate date = LocalDate.parse(dateStr,
                            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                    dates.add(date);
                    ohlcvData.add(row);
                } catch (Exception e) {
                    log.warn("Failed to parse date from row: {}", Arrays.toString(row), e);
                }
            }

            log.info("Parsed {} rows from Tushare response", ohlcvData.size());

            if (ohlcvData.size() < 10) {
                return new KLineResult(List.of(), ohlcvData.isEmpty());
            }

            // Calculate MACD (12, 26, 9)
            BigDecimal[] ema12 = calculateEMA(ohlcvData, closeIdx, 12);
            BigDecimal[] ema26 = calculateEMA(ohlcvData, closeIdx, 26);
            BigDecimal[] dif = new BigDecimal[ohlcvData.size()];
            BigDecimal[] dea = new BigDecimal[ohlcvData.size()];
            BigDecimal[] macd = new BigDecimal[ohlcvData.size()];

            for (int i = 0; i < ohlcvData.size(); i++) {
                if (ema12[i] != null && ema26[i] != null) {
                    dif[i] = ema12[i].subtract(ema26[i]);
                }
                if (dif[i] != null) {
                    if (dea[i - 1] != null) {
                        dea[i] = dea[i - 1].multiply(BigDecimal.valueOf(0.8))
                                .add(dif[i].multiply(BigDecimal.valueOf(0.2)));
                    } else {
                        dea[i] = dif[i];
                    }
                    macd[i] = dif[i].subtract(dea[i]).multiply(BigDecimal.valueOf(2));
                }
            }

            // Calculate KDJ (9, 3, 3)
            int kdjN = 9;
            BigDecimal[] rsv = new BigDecimal[ohlcvData.size()];
            BigDecimal[] kdjK = new BigDecimal[ohlcvData.size()];
            BigDecimal[] kdjD = new BigDecimal[ohlcvData.size()];
            BigDecimal[] kdjJ = new BigDecimal[ohlcvData.size()];

            for (int i = 0; i < ohlcvData.size(); i++) {
                if (i < kdjN - 1) continue;

                BigDecimal sumHigh = BigDecimal.ZERO;
                BigDecimal sumLow = BigDecimal.ZERO;
                int validCount = 0;
                for (int j = i - kdjN + 1; j <= i; j++) {
                    if (ohlcvData.get(j)[highIdx] != null && ohlcvData.get(j)[lowIdx] != null) {
                        sumHigh = sumHigh.add((BigDecimal) ohlcvData.get(j)[highIdx]);
                        sumLow = sumLow.add((BigDecimal) ohlcvData.get(j)[lowIdx]);
                        validCount++;
                    }
                }

                if (validCount > 0 && ohlcvData.get(i)[closeIdx] != null) {
                    BigDecimal highN = sumHigh.divide(BigDecimal.valueOf(validCount), 4, RoundingMode.HALF_UP);
                    BigDecimal lowN = sumLow.divide(BigDecimal.valueOf(validCount), 4, RoundingMode.HALF_UP);
                    BigDecimal close = (BigDecimal) ohlcvData.get(i)[closeIdx];

                    if (highN.compareTo(lowN) != 0) {
                        rsv[i] = close.subtract(lowN)
                                .divide(highN.subtract(lowN), 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                    }

                    if (rsv[i] != null) {
                        if (kdjK[i - 1] != null && kdjD[i - 1] != null) {
                            kdjK[i] = kdjK[i - 1].multiply(BigDecimal.valueOf(2))
                                    .add(rsv[i])
                                    .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
                            kdjD[i] = kdjD[i - 1].multiply(BigDecimal.valueOf(2))
                                    .add(kdjK[i])
                                    .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
                        } else {
                            kdjK[i] = rsv[i];
                            kdjD[i] = rsv[i];
                        }
                        kdjJ[i] = kdjK[i].multiply(BigDecimal.valueOf(3))
                                .subtract(kdjD[i].multiply(BigDecimal.valueOf(2)));
                    }
                }
            }

            // Build result rows (newest first)
            List<KLineRow> rows = new ArrayList<>();
            for (int i = ohlcvData.size() - 1; i >= 0; i--) {
                if (rows.size() >= count) break;

                LocalDate date = dates.get(i);
                Object[] row = ohlcvData.get(i);

                long timestamp = date.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai"))
                        .toInstant().toEpochMilli();

                rows.add(new KLineRow(
                        timestamp,
                        (BigDecimal) row[openIdx],
                        (BigDecimal) row[highIdx],
                        (BigDecimal) row[lowIdx],
                        (BigDecimal) row[closeIdx],
                        (BigDecimal) row[volIdx],
                        dif[i],
                        dea[i],
                        macd[i],
                        kdjK[i],
                        kdjD[i],
                        kdjJ[i]
                ));
            }

            // Check if suspended (latest data is older than last trading day)
            boolean suspended = false;
            if (!rows.isEmpty()) {
                LocalDate latestDate = rows.get(0).toLocalDate();
                // Only mark as suspended if it's NOT a trading day today AND latest data is before yesterday
                if (!isTradingDay(LocalDate.now())) {
                    // Today is not a trading day, check if data is from last trading day
                    LocalDate lastTradingDay = latestDate;
                    for (int i = 1; i <= 7; i++) {
                        LocalDate checkDate = LocalDate.now().minusDays(i);
                        if (isTradingDay(checkDate)) {
                            lastTradingDay = checkDate;
                            break;
                        }
                    }
                    if (latestDate.isBefore(lastTradingDay)) {
                        suspended = true;
                        log.info("Stock {} appears suspended, latest date: {}, last trading day: {}",
                                stockCode, latestDate, lastTradingDay);
                    }
                } else {
                    // Today is a trading day, data should be today
                    if (latestDate.isBefore(LocalDate.now())) {
                        suspended = true;
                        log.info("Stock {} appears suspended, latest date: {}", stockCode, latestDate);
                    }
                }
            }

            return new KLineResult(rows, suspended);

        } catch (Exception e) {
            log.error("Failed to fetch K-line from Tushare for {}", stockCode, e);
            return new KLineResult(List.of(), false);
        }
    }

    private BigDecimal[] calculateEMA(List<Object[]> data, int closeIdx, int period) {
        BigDecimal[] ema = new BigDecimal[data.size()];
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));

        for (int i = 0; i < data.size(); i++) {
            if (i < period - 1) {
                continue;
            }

            Object closeObj = data.get(i)[closeIdx];
            if (closeObj == null) continue;

            if (i == period - 1) {
                // Simple average for first EMA value
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = 0; j < period; j++) {
                    Object obj = data.get(j)[closeIdx];
                    if (obj != null) {
                        sum = sum.add((BigDecimal) obj);
                    }
                }
                ema[i] = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
            } else if (ema[i - 1] != null) {
                BigDecimal close = (BigDecimal) closeObj;
                ema[i] = close.subtract(ema[i - 1])
                        .multiply(multiplier)
                        .add(ema[i - 1]);
            }
        }
        return ema;
    }
}
