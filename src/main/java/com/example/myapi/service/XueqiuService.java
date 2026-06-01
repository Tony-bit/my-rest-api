package com.example.myapi.service;

import com.example.myapi.exception.XueqiuApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for interacting with Xueqiu K-line API.
 * Converts A-share stock codes to Xueqiu format and fetches daily KDJ/MACD indicators.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XueqiuService {

    private static final String XUEQIU_BASE_URL = "https://stock.xueqiu.com/v5/stock/chart/kline.json";
    private static final Pattern VALID_STOCK_CODE = Pattern.compile("^[036][0-9]{5}$");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Result of fetching K-line data from Xueqiu.
     */
    public record KLineResult(
            List<KLineRow> rows,
            boolean suspended
    ) {}

    /**
     * A single row of K-line data with indicator values.
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
                    .atZone(ZoneId.of("Asia/Shanghai"))
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
     * Validates if the stock code is a valid 6-digit A-share code.
     */
    public boolean isValidStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return false;
        }
        return VALID_STOCK_CODE.matcher(stockCode).matches();
    }

    /**
     * Converts a 6-digit A-share code to Xueqiu format.
     * 6xxxxx -> SH6xxxxx
     * 0xxxxx -> SZ0xxxxx
     * 3xxxxx -> SZ3xxxxx
     */
    public String toXueqiuCode(String stockCode) {
        if (!isValidStockCode(stockCode)) {
            throw new IllegalArgumentException("Invalid stock code: " + stockCode);
        }
        if (stockCode.startsWith("6")) {
            return "SH" + stockCode;
        }
        // 0xxxxx and 3xxxxx are both Shenzhen
        return "SZ" + stockCode;
    }

    /**
     * Fetches K-line data with indicators from Xueqiu.
     *
     * @param stockCode 6-digit A-share stock code
     * @param cookie Xueqiu authentication cookie
     * @param count Number of rows to fetch (should be at least 60)
     * @return KLineResult containing rows sorted by date descending, or suspended flag
     * @throws XueqiuApiException if API call fails
     */
    public KLineResult fetchKLineData(String stockCode, String cookie, int count) {
        String xueqiuCode = toXueqiuCode(stockCode);

        // begin参数是当前时间戳，count使用负数表示往前取数据
        long begin = System.currentTimeMillis();
        String url = String.format("%s?symbol=%s&begin=%d&period=day&type=before&count=-%d&indicator=kline,ma,macd,kdj",
                XUEQIU_BASE_URL, xueqiuCode, begin, Math.abs(count));

        log.info("Fetching K-line from Xueqiu: symbol={}, count={}", xueqiuCode, count);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Cookie", cookie)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Origin", "https://xueqiu.com")
                    .header("Referer", "https://xueqiu.com")
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 403) {
                throw new XueqiuApiException("Xueqiu cookie is invalid or expired");
            }

            if (response.statusCode() != 200) {
                throw new XueqiuApiException("Xueqiu API returned status " + response.statusCode());
            }

            return parseResponse(response.body(), stockCode);

        } catch (XueqiuApiException e) {
            throw e;
        } catch (Exception e) {
            throw new XueqiuApiException("Failed to fetch K-line from Xueqiu", e);
        }
    }

    private KLineResult parseResponse(String body, String stockCode) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.get("data");
        if (data == null || !data.has("items")) {
            return new KLineResult(List.of(), false);
        }

        JsonNode items = data.get("items");
        if (items == null || items.isEmpty()) {
            return new KLineResult(List.of(), false);
        }

        // Parse column names
        JsonNode columns = data.get("columns");
        if (columns == null) {
            return new KLineResult(List.of(), false);
        }

        List<String> columnNames = new ArrayList<>();
        for (JsonNode col : columns) {
            columnNames.add(col.asText());
        }

        List<KLineRow> rows = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isArray() || item.size() == 0) {
                continue;
            }

            try {
                KLineRow row = parseRow(item, columnNames);
                if (row != null) {
                    rows.add(row);
                }
            } catch (Exception e) {
                log.warn("Failed to parse K-line row for {}: {}", stockCode, e.getMessage());
            }
        }

        // Sort by timestamp descending (newest first)
        rows.sort((a, b) -> Long.compare(b.timestamp(), a.timestamp()));

        // Check if the stock is suspended (latest date before today)
        boolean suspended = false;
        if (!rows.isEmpty()) {
            LocalDate latestDate = rows.get(0).toLocalDate();
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            if (latestDate.isBefore(today)) {
                suspended = true;
                log.info("Stock {} appears suspended, latest date: {}", stockCode, latestDate);
            }
        }

        return new KLineResult(rows, suspended);
    }

    private KLineRow parseRow(JsonNode item, List<String> columns) {
        long timestamp = 0;
        BigDecimal open = null, high = null, low = null, close = null, volume = null;
        BigDecimal dif = null, dea = null, macd = null, kdjk = null, kdjd = null, kdjj = null;

        for (int i = 0; i < columns.size() && i < item.size(); i++) {
            String colName = columns.get(i);
            JsonNode value = item.get(i);
            if (value == null || value.isNull()) {
                continue;
            }

            switch (colName) {
                case "timestamp" -> timestamp = value.asLong();
                case "open" -> open = toDecimal(value);
                case "high" -> high = toDecimal(value);
                case "low" -> low = toDecimal(value);
                case "close" -> close = toDecimal(value);
                case "volume" -> volume = toDecimal(value);
                case "dif" -> dif = toDecimal(value);
                case "dea" -> dea = toDecimal(value);
                case "macd" -> macd = toDecimal(value);
                case "kdjk" -> kdjk = toDecimal(value);
                case "kdjd" -> kdjd = toDecimal(value);
                case "kdjj" -> kdjj = toDecimal(value);
            }
        }

        if (timestamp == 0 || close == null) {
            return null;
        }

        return new KLineRow(timestamp, open, high, low, close, volume,
                dif, dea, macd, kdjk, kdjd, kdjj);
    }

    private BigDecimal toDecimal(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return BigDecimal.valueOf(node.doubleValue());
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
