# 股票技术指标监控实现计划

**Metadata:**
- 状态: draft
- 预估工时: 16h

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现股票技术指标每日监控功能，支持 KDJ/MACD 信号检测和微信测试号通知。

**Architecture:**
- 后端采用 Spring Boot + JPA，监控模块独立于现有交易模块
- 数据来源：雪球 K 线接口（Cookie 认证）
- 通知方式：微信公众平台测试号模板消息
- 前端使用 React + React Router，新增监控导航菜单

---

## 文件结构

### 后端新增文件

| 文件路径 | 职责 |
|----------|------|
| `entity/StockMonitorConfig.java` | 雪球Cookie和微信测试号配置单例 |
| `entity/StockWatch.java` | 监控股票实体 |
| `entity/StockIndicatorSnapshot.java` | 每日指标快照 |
| `entity/StockSignalLog.java` | 信号触发审计日志 |
| `repository/StockMonitorConfigRepository.java` | 配置仓库 |
| `repository/StockWatchRepository.java` | 监控股票仓库 |
| `repository/StockIndicatorSnapshotRepository.java` | 指标快照仓库 |
| `repository/StockSignalLogRepository.java` | 信号日志仓库 |
| `service/XueqiuService.java` | 雪球K线数据获取 |
| `service/WeChatNotificationService.java` | 微信模板消息发送 |
| `service/StockSignalService.java` | KDJ/MACD信号评估（纯函数） |
| `service/StockMonitorService.java` | 监控核心业务逻辑 |
| `controller/StockMonitorController.java` | 监控股票CRUD API |
| `controller/StockSignalController.java` | 信号查询API |
| `dto/StockMonitorDTO.java` | 配置和股票DTO |
| `scheduler/StockMonitorTask.java` | 收盘后定时任务(17:10) |

### 前端新增文件

| 文件路径 | 职责 |
|----------|------|
| `api/stockMonitor.ts` | 监控模块API客户端 |
| `hooks/useStockMonitor.ts` | 监控模块React Query hooks |
| `types/stockMonitor.ts` | TypeScript类型定义 |
| `pages/StockMonitorList.tsx` | 监控列表页面 |
| `pages/StockMonitorDetail.tsx` | 股票详情页面 |

### 前端修改文件

| 文件路径 | 修改内容 |
|----------|----------|
| `App.tsx` | 添加路由 `/stock-monitor`, `/stock-monitor/:code` |
| `components/layout/AppLayout.tsx` | 添加"技术指标监控"导航项 |

### 数据库迁移

| 文件路径 | 内容 |
|----------|------|
| `V4__stock_indicator_monitor.sql` | 已存在，需确认字段正确 |

---

## 执行顺序

```
Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 → Task 7 → Task 8
```

---

## Task 1: 后端基础设施 - 实体类

> **前置条件:** 无

**Files:**
- Create: `src/main/java/com/example/myapi/entity/StockMonitorConfig.java`
- Create: `src/main/java/com/example/myapi/entity/StockWatch.java`
- Create: `src/main/java/com/example/myapi/entity/StockIndicatorSnapshot.java`
- Create: `src/main/java/com/example/myapi/entity/StockSignalLog.java`
- Create: `src/main/java/com/example/myapi/entity/SignalState.java`
- Create: `src/main/java/com/example/myapi/entity/NotificationStatus.java`

---

### Task 1.1: SignalState 枚举

- [ ] **Step 1: Create enum**

```java
// src/main/java/com/example/myapi/entity/SignalState.java
package com.example.myapi.entity;

public enum SignalState {
    NONE,       // 无状态
    WATCHING,   // 观察中
    J_BOTTOM,   // J值触底
    J_BOTTOM_2, // J值连续触底
    GOLDEN_CROSS,  // 金叉信号
    DEAD_CROSS     // 死叉信号
}
```

---

### Task 1.2: NotificationStatus 枚举

- [ ] **Step 1: Create enum**

```java
// src/main/java/com/example/myapi/entity/NotificationStatus.java
package com.example.myapi.entity;

public enum NotificationStatus {
    NOT_SENT,
    SENT,
    FAILED
}
```

---

### Task 1.3: StockMonitorConfig 实体

- [ ] **Step 1: Create entity**

```java
// src/main/java/com/example/myapi/entity/StockMonitorConfig.java
package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

    // Xueqiu configuration
    @Column(name = "xueqiu_cookie", columnDefinition = "TEXT")
    private String xueqiuCookie;

    @Column(name = "xueqiu_configured", nullable = false)
    @Builder.Default
    private Boolean xueqiuConfigured = false;

    // WeChat test account configuration
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

    // Business methods
    public boolean hasXueqiuConfig() {
        return xueqiuCookie != null && !xueqiuCookie.isBlank();
    }

    public boolean hasWeChatConfig() {
        return wechatAppId != null && !wechatAppId.isBlank()
            && wechatAppSecret != null && !wechatAppSecret.isBlank()
            && wechatTemplateId != null && !wechatTemplateId.isBlank()
            && wechatOpenId != null && !wechatOpenId.isBlank();
    }

    public String getWechatAppSecretMasked() {
        if (wechatAppSecret == null || wechatAppSecret.length() < 8) {
            return "****";
        }
        return wechatAppSecret.substring(0, 4) + "****" + wechatAppSecret.substring(wechatAppSecret.length() - 4);
    }
}
```

---

### Task 1.4: StockWatch 实体

- [ ] **Step 1: Create entity**

```java
// src/main/java/com/example/myapi/entity/StockWatch.java
package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_watch", uniqueConstraints = {
    @UniqueConstraint(columnNames = "stock_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockWatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "stock_name", length = 100)
    private String stockName;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_state", nullable = false, length = 20)
    @Builder.Default
    private SignalState signalState = SignalState.NONE;

    @Column(name = "watch_start_date")
    private LocalDate watchStartDate;

    @Column(name = "last_signal_type", length = 20)
    private String lastSignalType;

    @Column(name = "last_signal_date")
    private LocalDate lastSignalDate;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_check_status", length = 30)
    private String lastCheckStatus;

    @Column(name = "last_check_message", length = 500)
    private String lastCheckMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (watchStartDate == null) {
            watchStartDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Business methods
    public boolean isWatching() {
        return enabled && signalState != SignalState.NONE;
    }

    public String getDisplayName() {
        return stockName != null && !stockName.isBlank() ? stockName : stockCode;
    }
}
```

---

### Task 1.5: StockIndicatorSnapshot 实体

- [ ] **Step 1: Create entity**

```java
// src/main/java/com/example/myapi/entity/StockIndicatorSnapshot.java
package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_indicator_snapshot", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"stock_code", "trade_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockIndicatorSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", precision = 18, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 18, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 18, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "dif", precision = 18, scale = 6)
    private BigDecimal dif;

    @Column(name = "dea", precision = 18, scale = 6)
    private BigDecimal dea;

    @Column(name = "macd", precision = 18, scale = 6)
    private BigDecimal macd;

    @Column(name = "kdjk", precision = 18, scale = 6)
    private BigDecimal kdjk;

    @Column(name = "kdjd", precision = 18, scale = 6)
    private BigDecimal kdjd;

    @Column(name = "kdjj", precision = 18, scale = 6)
    private BigDecimal kdjj;

    @Column(name = "source_timestamp")
    private Long sourceTimestamp;

    @Column(name = "last_checked_at", nullable = false)
    private LocalDateTime lastCheckedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        lastCheckedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastCheckedAt = LocalDateTime.now();
    }
}
```

---

### Task 1.6: StockSignalLog 实体

- [ ] **Step 1: Create entity**

```java
// src/main/java/com/example/myapi/entity/StockSignalLog.java
package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_signal_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockSignalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "signal_type", nullable = false, length = 20)
    private String signalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 20)
    private TriggerSource triggerSource;

    @Column(name = "close_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "j_today", precision = 18, scale = 6)
    private BigDecimal jToday;

    @Column(name = "j_yesterday", precision = 18, scale = 6)
    private BigDecimal jYesterday;

    @Column(name = "macd_today", precision = 18, scale = 6)
    private BigDecimal macdToday;

    @Column(name = "macd_yesterday", precision = 18, scale = 6)
    private BigDecimal macdYesterday;

    @Column(name = "evaluation_summary", length = 500)
    private String evaluationSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_status", nullable = false, length = 30)
    @Builder.Default
    private NotificationStatus notificationStatus = NotificationStatus.NOT_SENT;

    @Column(name = "notification_attempts", nullable = false)
    @Builder.Default
    private Integer notificationAttempts = 0;

    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;

    @Column(name = "notification_failure_reason", length = 500)
    private String notificationFailureReason;

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

    public enum TriggerSource {
        SCHEDULED,  // 定时任务触发
        MANUAL,     // 手动检查
        RETRY       // 重试
    }
}
```

---

## Task 2: Repository 接口

> **前置条件:** Task 1 已完成

**Files:**
- Create: `src/main/java/com/example/myapi/repository/StockMonitorConfigRepository.java`
- Create: `src/main/java/com/example/myapi/repository/StockWatchRepository.java`
- Create: `src/main/java/com/example/myapi/repository/StockIndicatorSnapshotRepository.java`
- Create: `src/main/java/com/example/myapi/repository/StockSignalLogRepository.java`

---

### Task 2.1: StockMonitorConfigRepository

- [ ] **Step 1: Create repository**

```java
// src/main/java/com/example/myapi/repository/StockMonitorConfigRepository.java
package com.example.myapi.repository;

import com.example.myapi.entity.StockMonitorConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockMonitorConfigRepository extends JpaRepository<StockMonitorConfig, Long> {

    default StockMonitorConfig getSingleton() {
        return findById(1L).orElseGet(() -> {
            StockMonitorConfig config = StockMonitorConfig.builder().build();
            return save(config);
        });
    }

    Optional<StockMonitorConfig> findById(Long id);
}
```

---

### Task 2.2: StockWatchRepository

- [ ] **Step 1: Create repository**

```java
// src/main/java/com/example/myapi/repository/StockWatchRepository.java
package com.example.myapi.repository;

import com.example.myapi.entity.StockWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockWatchRepository extends JpaRepository<StockWatch, Long> {

    List<StockWatch> findAll();

    List<StockWatch> findByEnabledTrue();

    Optional<StockWatch> findByStockCode(String stockCode);

    boolean existsByStockCode(String stockCode);
}
```

---

### Task 2.3: StockIndicatorSnapshotRepository

- [ ] **Step 1: Create repository**

```java
// src/main/java/com/example/myapi/repository/StockIndicatorSnapshotRepository.java
package com.example.myapi.repository;

import com.example.myapi.entity.StockIndicatorSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockIndicatorSnapshotRepository extends JpaRepository<StockIndicatorSnapshot, Long> {

    Optional<StockIndicatorSnapshot> findByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    List<StockIndicatorSnapshot> findByStockCodeOrderByTradeDateDesc(String stockCode);

    @Query("SELECT s FROM StockIndicatorSnapshot s WHERE s.stockCode = :stockCode AND s.tradeDate <= :date ORDER BY s.tradeDate DESC LIMIT :limit")
    List<StockIndicatorSnapshot> findLatestByStockCode(@Param("stockCode") String stockCode, @Param("date") LocalDate date, @Param("limit") int limit);

    @Query("SELECT s FROM StockIndicatorSnapshot s WHERE s.stockCode = :stockCode ORDER BY s.tradeDate DESC LIMIT 2")
    List<StockIndicatorSnapshot> findLatestTwoByStockCode(@Param("stockCode") String stockCode);
}
```

---

### Task 2.4: StockSignalLogRepository

- [ ] **Step 1: Create repository**

```java
// src/main/java/com/example/myapi/repository/StockSignalLogRepository.java
package com.example.myapi.repository;

import com.example.myapi.entity.NotificationStatus;
import com.example.myapi.entity.StockSignalLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockSignalLogRepository extends JpaRepository<StockSignalLog, Long> {

    List<StockSignalLog> findByStockCodeOrderByCreatedAtDesc(String stockCode);

    List<StockSignalLog> findByStockCodeAndTradeDate(String stockCode, LocalDate tradeDate);

    List<StockSignalLog> findByNotificationStatus(NotificationStatus status);

    boolean existsByStockCodeAndTradeDateAndSignalType(String stockCode, LocalDate tradeDate, String signalType);
}
```

---

## Task 3: XueqiuService

> **前置条件:** Task 2 已完成

**Files:**
- Create: `src/main/java/com/example/myapi/service/XueqiuService.java`

---

### Task 3.1: XueqiuService

- [ ] **Step 1: Create service**

```java
// src/main/java/com/example/myapi/service/XueqiuService.java
package com.example.myapi.service;

import com.example.myapi.entity.StockMonitorConfig;
import com.example.myapi.repository.StockMonitorConfigRepository;
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
import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class XueqiuService {

    private static final String XUEQIU_KLINE_URL = "https://stock.xueqiu.com/v5/stock/chart/kline.json";
    private static final int DEFAULT_COUNT = 60;

    private final StockMonitorConfigRepository configRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * 获取股票K线数据，包含MA、MACD、KDJ指标
     */
    public Optional<XueqiuKLineResult> getKLineData(String stockCode) {
        return getKLineData(stockCode, DEFAULT_COUNT);
    }

    public Optional<XueqiuKLineResult> getKLineData(String stockCode, int count) {
        StockMonitorConfig config = configRepository.getSingleton();
        if (!config.hasXueqiuConfig()) {
            log.warn("Xueqiu cookie not configured");
            return Optional.empty();
        }

        try {
            String xueqiuCode = toXueqiuCode(stockCode);
            String url = String.format("%s?symbol=%s&period=day&type=before&count=%d&indicator=kline,ma,macd,kdj",
                    XUEQIU_KLINE_URL, xueqiuCode, count);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Cookie", config.getXueqiuCookie())
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://xueqiu.com")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Xueqiu API error: status={}, body={}", response.statusCode(), response.body());
                return Optional.empty();
            }

            return parseResponse(response.body(), stockCode);

        } catch (Exception e) {
            log.error("Failed to fetch K-line data from Xueqiu for {}", stockCode, e);
            return Optional.empty();
        }
    }

    /**
     * 将6位股票代码转换为雪球格式
     * 6xxxxx -> SH6xxxxx
     * 0xxxxx -> SZ0xxxxx
     * 3xxxxx -> SZ3xxxxx
     */
    public String toXueqiuCode(String stockCode) {
        if (stockCode == null || stockCode.length() < 6) {
            return stockCode;
        }
        String code = stockCode.length() > 6 ? stockCode.substring(0, 6) : stockCode;
        if (code.startsWith("6")) {
            return "SH" + code;
        }
        if (code.startsWith("3") || code.startsWith("0")) {
            return "SZ" + code;
        }
        return code;
    }

    private Optional<XueqiuKLineResult> parseResponse(String body, String stockCode) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode data = root.path("data");
        JsonNode items = data.path("item");

        if (!items.isArray() || items.isEmpty()) {
            log.warn("No K-line data from Xueqiu for {}", stockCode);
            return Optional.empty();
        }

        // 取最新的数据（最后一条）
        JsonNode latest = items.get(items.size() - 1);
        JsonNode timestamps = data.path("timestamp");

        long timestamp = timestamps.isArray() && timestamps.size() > 0
                ? timestamps.get(timestamps.size() - 1).asLong()
                : System.currentTimeMillis();

        return Optional.of(XueqiuKLineResult.builder()
                .stockCode(stockCode)
                .timestamp(timestamp)
                .open(new BigDecimal(latest.get(1).asText()))
                .close(new BigDecimal(latest.get(2).asText()))
                .high(new BigDecimal(latest.get(3).asText()))
                .low(new BigDecimal(latest.get(4).asText()))
                .volume(latest.get(5).asDouble())
                .ma5(parseDecimal(latest, 6))
                .ma10(parseDecimal(latest, 7))
                .ma20(parseDecimal(latest, 8))
                .dif(parseDecimal(latest, 9))
                .dea(parseDecimal(latest, 10))
                .macd(parseDecimal(latest, 11))
                .kdjk(parseDecimal(latest, 12))
                .kdjd(parseDecimal(latest, 13))
                .kdjj(parseDecimal(latest, 14))
                .build());
    }

    private BigDecimal parseDecimal(JsonNode node, int index) {
        if (index >= node.size()) return null;
        JsonNode val = node.get(index);
        if (val == null || val.isNull()) return null;
        try {
            return new BigDecimal(val.asText());
        } catch (Exception e) {
            return null;
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class XueqiuKLineResult {
        private String stockCode;
        private long timestamp;
        private BigDecimal open;
        private BigDecimal close;
        private BigDecimal high;
        private BigDecimal low;
        private double volume;
        private BigDecimal ma5;
        private BigDecimal ma10;
        private BigDecimal ma20;
        private BigDecimal dif;
        private BigDecimal dea;
        private BigDecimal macd;
        private BigDecimal kdjk;
        private BigDecimal kdjd;
        private BigDecimal kdjj;

        public LocalDateTime getTimestampAsDateTime() {
            return Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
        }
    }
}
```

---

## Task 4: StockSignalService (信号评估核心)

> **前置条件:** Task 3 已完成

**Files:**
- Create: `src/main/java/com/example/myapi/service/StockSignalService.java`
- Create: `src/test/java/com/example/myapi/service/StockSignalServiceTest.java`

---

### Task 4.1: StockSignalService

- [ ] **Step 1: Create service (pure functions)**

```java
// src/main/java/com/example/myapi/service/StockSignalService.java
package com.example.myapi.service;

import com.example.myapi.entity.SignalState;
import com.example.myapi.entity.StockIndicatorSnapshot;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * KDJ/MACD 信号评估服务（纯函数，无状态）
 */
@Service
public class StockSignalService {

    // KDJ 超卖阈值：J < 20
    private static final BigDecimal KDJ_OVERSOLD = new BigDecimal("20");

    // MACD 金叉/死叉判定阈值
    private static final BigDecimal MACD_CROSS_THRESHOLD = new BigDecimal("0");

    /**
     * 评估信号状态
     * @param current 今天指标
     * @param previous 昨天指标（可为null）
     * @param currentState 当前状态
     * @return 评估结果
     */
    public SignalEvaluationResult evaluate(List<StockIndicatorSnapshot> history, SignalState currentState) {
        if (history == null || history.isEmpty()) {
            return SignalEvaluationResult.builder()
                    .signalState(currentState)
                    .signalType(null)
                    .summary("无历史数据")
                    .build();
        }

        StockIndicatorSnapshot today = history.get(0);
        StockIndicatorSnapshot yesterday = history.size() > 1 ? history.get(1) : null;

        BigDecimal jToday = today.getKdjj();
        BigDecimal jYesterday = yesterday != null ? yesterday.getKdjj() : null;
        BigDecimal macdToday = today.getMacd();
        BigDecimal macdYesterday = yesterday != null ? yesterday.getMacd() : null;

        return evaluateSignal(jToday, jYesterday, macdToday, macdYesterday, currentState, today);
    }

    public SignalEvaluationResult evaluateSignal(
            BigDecimal jToday,
            BigDecimal jYesterday,
            BigDecimal macdToday,
            BigDecimal macdYesterday,
            SignalState currentState,
            StockIndicatorSnapshot snapshot) {

        StringBuilder summary = new StringBuilder();

        // 评估 J 值状态
        boolean jBelowToday = jToday != null && jToday.compareTo(KDJ_OVERSOLD) < 0;
        boolean jBelowYesterday = jYesterday != null && jYesterday.compareTo(KDJ_OVERSOLD) < 0;

        // 评估 MACD 状态
        boolean macdPositiveToday = macdToday != null && macdToday.compareTo(BigDecimal.ZERO) > 0;
        boolean macdPositiveYesterday = macdYesterday != null && macdYesterday.compareTo(BigDecimal.ZERO) > 0;
        boolean macdCrossUp = isCrossUp(macdYesterday, macdToday);
        boolean macdCrossDown = isCrossDown(macdYesterday, macdToday);

        SignalState newState = currentState;
        String signalType = null;

        switch (currentState) {
            case NONE:
                // J值触底 -> WATCHING
                if (jBelowToday) {
                    newState = SignalState.WATCHING;
                    summary.append(String.format("J值触底(%.2f<20)，进入观察状态。", jToday));
                }
                break;

            case WATCHING:
                // J值连续触底 -> J_BOTTOM
                if (jBelowToday && jBelowYesterday) {
                    newState = SignalState.J_BOTTOM;
                    summary.append(String.format("J值连续触底(今日%.2f,昨日%.2f)。", jToday, jYesterday));
                } else if (!jBelowToday) {
                    // J值恢复，取消观察
                    newState = SignalState.NONE;
                    summary.append(String.format("J值恢复至%.2f，取消观察。", jToday));
                }
                break;

            case J_BOTTOM:
                // J值再次触底 -> J_BOTTOM_2
                if (jBelowToday) {
                    newState = SignalState.J_BOTTOM_2;
                    summary.append(String.format("J值再次触底(%.2f)，进入二次确认。", jToday));
                } else if (!jBelowYesterday) {
                    // 昨天J值已恢复
                    newState = SignalState.NONE;
                    summary.append(String.format("J值恢复至%.2f，重置状态。", jToday));
                }
                break;

            case J_BOTTOM_2:
                // MACD金叉 + J值仍低 -> GOLDEN_CROSS
                if (macdCrossUp && macdPositiveToday && jBelowToday) {
                    newState = SignalState.GOLDEN_CROSS;
                    signalType = "GOLDEN_CROSS";
                    summary.append(String.format("【买入信号】MACD金叉(%.4f->%.4f)且J值低位(%.2f)！",
                            macdYesterday, macdToday, jToday));
                } else if (!jBelowToday) {
                    newState = SignalState.NONE;
                    summary.append(String.format("J值恢复至%.2f，重置状态。", jToday));
                } else {
                    newState = SignalState.WATCHING;
                    summary.append(String.format("等待MACD金叉确认。当前MACD=%.4f，J=%.2f。", macdToday, jToday));
                }
                break;

            case GOLDEN_CROSS:
                // MACD死叉 -> DEAD_CROSS 或 重置
                if (macdCrossDown && !macdPositiveToday) {
                    newState = SignalState.DEAD_CROSS;
                    signalType = "DEAD_CROSS";
                    summary.append(String.format("【卖出信号】MACD死叉(%.4f->%.4f)！", macdYesterday, macdToday));
                } else {
                    summary.append(String.format("持有中。MACD=%.4f，J=%.2f。", macdToday, jToday));
                }
                break;

            case DEAD_CROSS:
                // 重置到NONE
                newState = SignalState.NONE;
                summary.append("周期结束，重置状态。");
                break;
        }

        if (summary.length() == 0) {
            summary.append(String.format("J=%.2f, MACD=%.4f，无变化。",
                    jToday != null ? jToday : 0, macdToday != null ? macdToday : 0));
        }

        return SignalEvaluationResult.builder()
                .signalState(newState)
                .signalType(signalType)
                .jToday(jToday)
                .jYesterday(jYesterday)
                .macdToday(macdToday)
                .macdYesterday(macdYesterday)
                .summary(summary.toString())
                .snapshot(snapshot)
                .build();
    }

    private boolean isCrossUp(BigDecimal yesterday, BigDecimal today) {
        if (yesterday == null || today == null) return false;
        // 从负转正，或从下往上穿过零轴
        return (yesterday.compareTo(BigDecimal.ZERO) <= 0 && today.compareTo(BigDecimal.ZERO) > 0)
            || (yesterday.compareTo(today) < 0 && isNearZero(yesterday) && today.compareTo(BigDecimal.ZERO) > 0);
    }

    private boolean isCrossDown(BigDecimal yesterday, BigDecimal today) {
        if (yesterday == null || today == null) return false;
        return (yesterday.compareTo(BigDecimal.ZERO) >= 0 && today.compareTo(BigDecimal.ZERO) < 0)
            || (yesterday.compareTo(today) > 0 && isNearZero(yesterday) && today.compareTo(BigDecimal.ZERO) < 0);
    }

    private boolean isNearZero(BigDecimal value) {
        return value != null && value.abs().compareTo(new BigDecimal("0.01")) < 0;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SignalEvaluationResult {
        private SignalState signalState;
        private String signalType;
        private BigDecimal jToday;
        private BigDecimal jYesterday;
        private BigDecimal macdToday;
        private BigDecimal macdYesterday;
        private String summary;
        private StockIndicatorSnapshot snapshot;
    }
}
```

---

### Task 4.2: StockSignalServiceTest

- [ ] **Step 1: Create test**

```java
// src/test/java/com/example/myapi/service/StockSignalServiceTest.java
package com.example.myapi.service;

import com.example.myapi.entity.SignalState;
import com.example.myapi.entity.StockIndicatorSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StockSignalServiceTest {

    private StockSignalService service;

    @BeforeEach
    void setUp() {
        service = new StockSignalService();
    }

    private StockIndicatorSnapshot createSnapshot(String code, LocalDate date, BigDecimal kdjj, BigDecimal macd) {
        return StockIndicatorSnapshot.builder()
                .stockCode(code)
                .tradeDate(date)
                .closePrice(new BigDecimal("100.00"))
                .kdjj(kdjj)
                .macd(macd)
                .lastCheckedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void test_none_to_watching_when_j_below_20() {
        StockIndicatorSnapshot today = createSnapshot("600000", LocalDate.now(), new BigDecimal("15"), new BigDecimal("0.1"));

        var result = service.evaluate(Collections.singletonList(today), SignalState.NONE);

        assertEquals(SignalState.WATCHING, result.getSignalState());
        assertNull(result.getSignalType());
        assertTrue(result.getSummary().contains("J值触底"));
    }

    @Test
    void test_watching_to_j_bottom_when_j_below_twice() {
        StockIndicatorSnapshot yesterday = createSnapshot("600000", LocalDate.now().minusDays(1), new BigDecimal("18"), new BigDecimal("0.05"));
        StockIndicatorSnapshot today = createSnapshot("600000", LocalDate.now(), new BigDecimal("15"), new BigDecimal("0.1"));

        var result = service.evaluate(Arrays.asList(today, yesterday), SignalState.WATCHING);

        assertEquals(SignalState.J_BOTTOM, result.getSignalState());
        assertTrue(result.getSummary().contains("J值连续触底"));
    }

    @Test
    void test_j_bottom_2_to_golden_cross() {
        // J值连续触底后，MACD金叉
        StockIndicatorSnapshot twoDaysAgo = createSnapshot("600000", LocalDate.now().minusDays(2), new BigDecimal("19"), new BigDecimal("-0.01"));
        StockIndicatorSnapshot yesterday = createSnapshot("600000", LocalDate.now().minusDays(1), new BigDecimal("15"), new BigDecimal("0.02"));
        StockIndicatorSnapshot today = createSnapshot("600000", LocalDate.now(), new BigDecimal("12"), new BigDecimal("0.05"));

        var result = service.evaluate(Arrays.asList(today, yesterday, twoDaysAgo), SignalState.J_BOTTOM_2);

        assertEquals(SignalState.GOLDEN_CROSS, result.getSignalState());
        assertEquals("GOLDEN_CROSS", result.getSignalType());
        assertTrue(result.getSummary().contains("买入信号"));
    }

    @Test
    void test_golden_cross_to_dead_cross() {
        StockIndicatorSnapshot yesterday = createSnapshot("600000", LocalDate.now().minusDays(1), new BigDecimal("10"), new BigDecimal("0.05"));
        StockIndicatorSnapshot today = createSnapshot("600000", LocalDate.now(), new BigDecimal("15"), new BigDecimal("-0.02"));

        var result = service.evaluate(Arrays.asList(today, yesterday), SignalState.GOLDEN_CROSS);

        assertEquals(SignalState.DEAD_CROSS, result.getSignalState());
        assertEquals("DEAD_CROSS", result.getSignalType());
        assertTrue(result.getSummary().contains("卖出信号"));
    }

    @Test
    void test_no_signal_when_no_history() {
        var result = service.evaluate(Collections.emptyList(), SignalState.NONE);

        assertEquals(SignalState.NONE, result.getSignalState());
        assertNull(result.getSignalType());
    }
}
```

---

## Task 5: WeChatNotificationService

> **前置条件:** Task 2 已完成

**Files:**
- Create: `src/main/java/com/example/myapi/service/WeChatNotificationService.java`
- Create: `src/test/java/com/example/myapi/service/WeChatNotificationServiceTest.java`

---

### Task 5.1: WeChatNotificationService

- [ ] **Step 1: Create service**

```java
// src/main/java/com/example/myapi/service/WeChatNotificationService.java
package com.example.myapi.service;

import com.example.myapi.entity.StockMonitorConfig;
import com.example.myapi.repository.StockMonitorConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatNotificationService {

    private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String TEMPLATE_MSG_URL = "https://api.weixin.qq.com/cgi-bin/message/template/send";

    private final StockMonitorConfigRepository configRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Simple in-memory cache for access token
    private String cachedAccessToken;
    private long tokenExpiresAt;

    /**
     * 发送微信模板消息
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param signalType 信号类型
     * @param closePrice 收盘价
     * @return 发送结果
     */
    public SendResult sendSignalNotification(String stockCode, String stockName, String signalType, String closePrice) {
        StockMonitorConfig config = configRepository.getSingleton();
        if (!config.hasWeChatConfig()) {
            return SendResult.failure("微信配置未完成");
        }

        try {
            // 获取 access token
            Optional<String> tokenOpt = getAccessToken(config);
            if (tokenOpt.isEmpty()) {
                return SendResult.failure("获取access_token失败");
            }
            String accessToken = tokenOpt.get();

            // 构建模板消息
            String message = buildTemplateMessage(config.getWechatOpenId(), config.getWechatTemplateId(),
                    stockCode, stockName, signalType, closePrice);

            // 发送消息
            String url = TEMPLATE_MSG_URL + "?access_token=" + accessToken;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(message))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                int errcode = (int) result.getOrDefault("errcode", -1);
                if (errcode == 0) {
                    log.info("WeChat notification sent: stock={}, signal={}", stockCode, signalType);
                    return SendResult.success();
                } else {
                    String errmsg = (String) result.getOrDefault("errmsg", "unknown");
                    log.error("WeChat API error: errcode={}, errmsg={}", errcode, errmsg);
                    return SendResult.failure(errmsg);
                }
            }

            return SendResult.failure("HTTP " + response.statusCode());

        } catch (Exception e) {
            log.error("Failed to send WeChat notification", e);
            return SendResult.failure(e.getMessage());
        }
    }

    private Optional<String> getAccessToken(StockMonitorConfig config) {
        // Check cache
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return Optional.of(cachedAccessToken);
        }

        try {
            String url = String.format("%s?grant_type=client_credential&appid=%s&secret=%s",
                    ACCESS_TOKEN_URL, config.getWechatAppId(), config.getWechatAppSecret());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
                String accessToken = (String) result.get("access_token");
                int expiresIn = (int) result.getOrDefault("expires_in", 7200);

                if (accessToken != null) {
                    cachedAccessToken = accessToken;
                    tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 300) * 1000L; // 提前5分钟过期
                    return Optional.of(accessToken);
                }
            }
        } catch (Exception e) {
            log.error("Failed to get access token", e);
        }
        return Optional.empty();
    }

    private String buildTemplateMessage(String openId, String templateId,
            String stockCode, String stockName, String signalType, String closePrice) throws Exception {
        Map<String, Object> data = Map.of(
                "first", Map.of("value", signalType.equals("GOLDEN_CROSS") ? "【买入信号提醒】" : "【卖出信号提醒】"),
                "keyword1", Map.of("value", stockName + " (" + stockCode + ")"),
                "keyword2", Map.of("value", signalType.equals("GOLDEN_CROSS") ? "MACD金叉 - 买入" : "MACD死叉 - 卖出"),
                "keyword3", Map.of("value", LocalDateTime.now().toString()),
                "remark", Map.of("value", "收盘价: " + closePrice)
        );

        Map<String, Object> body = Map.of(
                "touser", openId,
                "template_id", templateId,
                "data", data
        );

        return objectMapper.writeValueAsString(body);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SendResult {
        private boolean success;
        private String errorMessage;

        public static SendResult success() {
            return SendResult.builder().success(true).build();
        }

        public static SendResult failure(String message) {
            return SendResult.builder().success(false).errorMessage(message).build();
        }
    }
}
```

---

## Task 6: StockMonitorService

> **前置条件:** Task 3, 4, 5 已完成

**Files:**
- Create: `src/main/java/com/example/myapi/service/StockMonitorService.java`
- Create: `src/main/java/com/example/myapi/dto/StockMonitorDTO.java`
- Create: `src/test/java/com/example/myapi/service/StockMonitorServiceTest.java`

---

### Task 6.1: StockMonitorDTO

- [ ] **Step 1: Create DTO**

```java
// src/main/java/com/example/myapi/dto/StockMonitorDTO.java
package com.example.myapi.dto;

import com.example.myapi.entity.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class StockMonitorDTO {

    // ========== StockWatch DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockWatchResponse {
        private Long id;
        private String stockCode;
        private String stockName;
        private Boolean enabled;
        private String signalState;
        private LocalDate watchStartDate;
        private String lastSignalType;
        private LocalDate lastSignalDate;
        private LocalDateTime lastCheckedAt;
        private String lastCheckStatus;
        private String lastCheckMessage;

        public static StockWatchResponse from(StockWatch watch) {
            return StockWatchResponse.builder()
                    .id(watch.getId())
                    .stockCode(watch.getStockCode())
                    .stockName(watch.getStockName())
                    .enabled(watch.getEnabled())
                    .signalState(watch.getSignalState().name())
                    .watchStartDate(watch.getWatchStartDate())
                    .lastSignalType(watch.getLastSignalType())
                    .lastSignalDate(watch.getLastSignalDate())
                    .lastCheckedAt(watch.getLastCheckedAt())
                    .lastCheckStatus(watch.getLastCheckStatus())
                    .lastCheckMessage(watch.getLastCheckMessage())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddStockRequest {
        private String stockCode;
        private String stockName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateStockRequest {
        private Boolean enabled;
        private String stockName;
    }

    // ========== Config DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigResponse {
        private Boolean xueqiuConfigured;
        private Boolean wechatConfigured;
        private String wechatAppIdMasked;
        private String wechatTemplateId;
        private LocalDateTime updatedAt;

        public static ConfigResponse from(StockMonitorConfig config) {
            return ConfigResponse.builder()
                    .xueqiuConfigured(config.getXueqiuConfigured())
                    .wechatConfigured(config.getWechatConfigured())
                    .wechatAppIdMasked(config.getWechatAppId() != null ? config.getWechatAppId() + "***" : null)
                    .wechatTemplateId(config.getWechatTemplateId())
                    .updatedAt(config.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateConfigRequest {
        private String xueqiuCookie;
        private String wechatAppId;
        private String wechatAppSecret;
        private String wechatTemplateId;
        private String wechatOpenId;
    }

    // ========== Signal DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalLogResponse {
        private Long id;
        private String stockCode;
        private LocalDate tradeDate;
        private String signalType;
        private String triggerSource;
        private BigDecimal closePrice;
        private BigDecimal jToday;
        private BigDecimal jYesterday;
        private BigDecimal macdToday;
        private BigDecimal macdYesterday;
        private String evaluationSummary;
        private String notificationStatus;
        private LocalDateTime notificationSentAt;
        private String notificationFailureReason;
        private LocalDateTime createdAt;

        public static SignalLogResponse from(StockSignalLog log) {
            return SignalLogResponse.builder()
                    .id(log.getId())
                    .stockCode(log.getStockCode())
                    .tradeDate(log.getTradeDate())
                    .signalType(log.getSignalType())
                    .triggerSource(log.getTriggerSource().name())
                    .closePrice(log.getClosePrice())
                    .jToday(log.getJToday())
                    .jYesterday(log.getJYesterday())
                    .macdToday(log.getMacdToday())
                    .macdYesterday(log.getMacdYesterday())
                    .evaluationSummary(log.getEvaluationSummary())
                    .notificationStatus(log.getNotificationStatus().name())
                    .notificationSentAt(log.getNotificationSentAt())
                    .notificationFailureReason(log.getNotificationFailureReason())
                    .createdAt(log.getCreatedAt())
                    .build();
        }
    }

    // ========== Check Result DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckResult {
        private String stockCode;
        private String stockName;
        private Boolean success;
        private String message;
        private String signalState;
        private String signalType;
        private BigDecimal closePrice;
        private BigDecimal jValue;
        private BigDecimal macd;
        private Boolean notificationSent;
    }

    // ========== Stock Detail DTO ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockDetailResponse {
        private StockWatchResponse watch;
        private List<IndicatorSnapshotResponse> snapshots;
        private List<SignalLogResponse> signalLogs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndicatorSnapshotResponse {
        private LocalDate tradeDate;
        private BigDecimal closePrice;
        private BigDecimal kdjk;
        private BigDecimal kdjd;
        private BigDecimal kdjj;
        private BigDecimal dif;
        private BigDecimal dea;
        private BigDecimal macd;

        public static IndicatorSnapshotResponse from(StockIndicatorSnapshot snapshot) {
            return IndicatorSnapshotResponse.builder()
                    .tradeDate(snapshot.getTradeDate())
                    .closePrice(snapshot.getClosePrice())
                    .kdjk(snapshot.getKdjk())
                    .kdjd(snapshot.getKdjd())
                    .kdjj(snapshot.getKdjj())
                    .dif(snapshot.getDif())
                    .dea(snapshot.getDea())
                    .macd(snapshot.getMacd())
                    .build();
        }
    }
}
```

---

### Task 6.2: StockMonitorService

- [ ] **Step 1: Create service**

```java
// src/main/java/com/example/myapi/service/StockMonitorService.java
package com.example.myapi.service;

import com.example.myapi.dto.StockMonitorDTO.*;
import com.example.myapi.entity.*;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMonitorService {

    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("^[036]\\d{5}$");

    private final StockMonitorConfigRepository configRepository;
    private final StockWatchRepository watchRepository;
    private final StockIndicatorSnapshotRepository snapshotRepository;
    private final StockSignalLogRepository signalLogRepository;
    private final XueqiuService xueqiuService;
    private final StockSignalService signalService;
    private final WeChatNotificationService weChatService;

    // ========== Stock Watch CRUD ==========

    @Transactional(readOnly = true)
    public List<StockWatchResponse> listStocks() {
        return watchRepository.findAll().stream()
                .map(StockWatchResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public StockWatchResponse addStock(String stockCode, String stockName) {
        validateStockCode(stockCode);

        if (watchRepository.existsByStockCode(stockCode)) {
            throw new BusinessException("股票代码 " + stockCode + " 已在监控列表中", 400);
        }

        StockWatch watch = StockWatch.builder()
                .stockCode(stockCode)
                .stockName(stockName)
                .enabled(true)
                .signalState(SignalState.NONE)
                .watchStartDate(LocalDate.now())
                .build();

        watch = watchRepository.save(watch);
        log.info("Added stock to watch list: {}", stockCode);
        return StockWatchResponse.from(watch);
    }

    @Transactional
    public void deleteStock(Long id) {
        StockWatch watch = watchRepository.findById(id)
                .orElseThrow(() -> new BusinessException("股票不存在", 404));
        watchRepository.delete(watch);
        log.info("Removed stock from watch list: {}", watch.getStockCode());
    }

    @Transactional
    public StockWatchResponse updateStock(Long id, UpdateStockRequest request) {
        StockWatch watch = watchRepository.findById(id)
                .orElseThrow(() -> new BusinessException("股票不存在", 404));

        if (request.getEnabled() != null) {
            watch.setEnabled(request.getEnabled());
        }
        if (request.getStockName() != null) {
            watch.setStockName(request.getStockName());
        }

        watch = watchRepository.save(watch);
        return StockWatchResponse.from(watch);
    }

    // ========== Config ==========

    @Transactional(readOnly = true)
    public ConfigResponse getConfig() {
        StockMonitorConfig config = configRepository.getSingleton();
        return ConfigResponse.from(config);
    }

    @Transactional
    public ConfigResponse updateConfig(UpdateConfigRequest request) {
        StockMonitorConfig config = configRepository.getSingleton();

        if (request.getXueqiuCookie() != null) {
            config.setXueqiuCookie(request.getXueqiuCookie());
            config.setXueqiuConfigured(request.getXueqiuCookie() != null && !request.getXueqiuCookie().isBlank());
        }

        if (request.getWechatAppId() != null || request.getWechatAppSecret() != null
                || request.getWechatTemplateId() != null || request.getWechatOpenId() != null) {
            if (request.getWechatAppId() != null) config.setWechatAppId(request.getWechatAppId());
            if (request.getWechatAppSecret() != null) config.setWechatAppSecret(request.getWechatAppSecret());
            if (request.getWechatTemplateId() != null) config.setWechatTemplateId(request.getWechatTemplateId());
            if (request.getWechatOpenId() != null) config.setWechatOpenId(request.getWechatOpenId());
            config.setWechatConfigured(config.hasWeChatConfig());
        }

        config = configRepository.save(config);
        log.info("Stock monitor config updated");
        return ConfigResponse.from(config);
    }

    // ========== Manual Check ==========

    @Transactional
    public CheckResult checkStock(String stockCode, StockSignalLog.TriggerSource source, boolean sendNotification) {
        Optional<StockWatch> watchOpt = watchRepository.findByStockCode(stockCode);
        if (watchOpt.isEmpty()) {
            return CheckResult.builder()
                    .stockCode(stockCode)
                    .success(false)
                    .message("股票不在监控列表中")
                    .build();
        }

        StockWatch watch = watchOpt.get();

        try {
            // 获取K线数据
            Optional<XueqiuService.XueqiuKLineResult> klineOpt = xueqiuService.getKLineData(stockCode);
            if (klineOpt.isEmpty()) {
                watch.setLastCheckStatus("FAILED");
                watch.setLastCheckMessage("无法获取K线数据");
                watch.setLastCheckedAt(LocalDateTime.now());
                watchRepository.save(watch);

                return CheckResult.builder()
                        .stockCode(stockCode)
                        .stockName(watch.getStockName())
                        .success(false)
                        .message("无法获取K线数据，请检查雪球Cookie配置")
                        .build();
            }

            XueqiuService.XueqiuKLineResult kline = klineOpt.get();

            // 保存指标快照
            LocalDate today = LocalDate.now();
            StockIndicatorSnapshot snapshot = snapshotRepository
                    .findByStockCodeAndTradeDate(stockCode, today)
                    .orElse(StockIndicatorSnapshot.builder()
                            .stockCode(stockCode)
                            .tradeDate(today)
                            .build());

            snapshot.setOpenPrice(kline.getOpen());
            snapshot.setClosePrice(kline.getClose());
            snapshot.setHighPrice(kline.getHigh());
            snapshot.setLowPrice(kline.getLow());
            snapshot.setDif(kline.getDif());
            snapshot.setDea(kline.getDea());
            snapshot.setMacd(kline.getMacd());
            snapshot.setKdjk(kline.getKdjk());
            snapshot.setKdjd(kline.getKdjd());
            snapshot.setKdjj(kline.getKdjj());
            snapshot.setSourceTimestamp(kline.getTimestamp());
            snapshot = snapshotRepository.save(snapshot);

            // 获取历史数据进行信号评估
            List<StockIndicatorSnapshot> history = snapshotRepository.findLatestByStockCode(stockCode, today, 60);

            // 评估信号
            StockSignalService.SignalEvaluationResult evalResult = signalService.evaluate(history, watch.getSignalState());

            // 更新监控状态
            watch.setSignalState(evalResult.getSignalState());
            watch.setLastCheckedAt(LocalDateTime.now());
            watch.setLastCheckStatus("OK");

            // 如果有信号，更新最后信号信息
            if (evalResult.getSignalType() != null) {
                watch.setLastSignalType(evalResult.getSignalType());
                watch.setLastSignalDate(today);
            }

            watch.setLastCheckMessage(evalResult.getSummary());
            watchRepository.save(watch);

            // 记录信号日志
            StockSignalLog signalLog = StockSignalLog.builder()
                    .stockCode(stockCode)
                    .tradeDate(today)
                    .signalType(evalResult.getSignalType())
                    .triggerSource(source)
                    .closePrice(kline.getClose())
                    .jToday(evalResult.getJToday())
                    .jYesterday(evalResult.getJYesterday())
                    .macdToday(evalResult.getMacdToday())
                    .macdYesterday(evalResult.getMacdYesterday())
                    .evaluationSummary(evalResult.getSummary())
                    .notificationStatus(NotificationStatus.NOT_SENT)
                    .build();
            signalLog = signalLogRepository.save(signalLog);

            // 发送微信通知（仅定时任务触发且有信号时）
            boolean notificationSent = false;
            if (sendNotification && evalResult.getSignalType() != null
                    && source == StockSignalLog.TriggerSource.SCHEDULED) {
                WeChatNotificationService.SendResult result = weChatService.sendSignalNotification(
                        stockCode,
                        watch.getStockName(),
                        evalResult.getSignalType(),
                        kline.getClose().toString()
                );

                if (result.isSuccess()) {
                    signalLog.setNotificationStatus(NotificationStatus.SENT);
                    signalLog.setNotificationSentAt(LocalDateTime.now());
                    notificationSent = true;
                } else {
                    signalLog.setNotificationStatus(NotificationStatus.FAILED);
                    signalLog.setNotificationFailureReason(result.getErrorMessage());
                }
                signalLog.setNotificationAttempts(signalLog.getNotificationAttempts() + 1);
                signalLogRepository.save(signalLog);
            }

            return CheckResult.builder()
                    .stockCode(stockCode)
                    .stockName(watch.getStockName())
                    .success(true)
                    .message(evalResult.getSummary())
                    .signalState(evalResult.getSignalState().name())
                    .signalType(evalResult.getSignalType())
                    .closePrice(kline.getClose())
                    .jValue(evalResult.getJToday())
                    .macd(evalResult.getMacdToday())
                    .notificationSent(notificationSent)
                    .build();

        } catch (Exception e) {
            log.error("Error checking stock {}", stockCode, e);
            watch.setLastCheckStatus("ERROR");
            watch.setLastCheckMessage(e.getMessage());
            watch.setLastCheckedAt(LocalDateTime.now());
            watchRepository.save(watch);

            return CheckResult.builder()
                    .stockCode(stockCode)
                    .stockName(watch.getStockName())
                    .success(false)
                    .message("检查失败: " + e.getMessage())
                    .build();
        }
    }

    // ========== Stock Detail ==========

    @Transactional(readOnly = true)
    public StockDetailResponse getStockDetail(String stockCode) {
        StockWatch watch = watchRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new BusinessException("股票不存在", 404));

        List<StockIndicatorSnapshot> snapshots = snapshotRepository.findByStockCodeOrderByTradeDateDesc(stockCode);
        List<StockSignalLog> logs = signalLogRepository.findByStockCodeOrderByCreatedAtDesc(stockCode);

        return StockDetailResponse.builder()
                .watch(StockWatchResponse.from(watch))
                .snapshots(snapshots.stream()
                        .map(IndicatorSnapshotResponse::from)
                        .limit(30)
                        .collect(Collectors.toList()))
                .signalLogs(logs.stream()
                        .map(SignalLogResponse::from)
                        .limit(20)
                        .collect(Collectors.toList()))
                .build();
    }

    // ========== Helpers ==========

    private void validateStockCode(String stockCode) {
        if (stockCode == null || !STOCK_CODE_PATTERN.matcher(stockCode).matches()) {
            throw new BusinessException("无效的股票代码，仅支持6位A股代码（0、3、6开头）", 400);
        }
    }

    // ========== Batch Operations for Scheduled Task ==========

    @Transactional
    public void checkAllEnabledStocks() {
        List<StockWatch> enabledStocks = watchRepository.findByEnabledTrue();
        log.info("Starting scheduled check for {} enabled stocks", enabledStocks.size());

        for (StockWatch watch : enabledStocks) {
            checkStock(watch.getStockCode(), StockSignalLog.TriggerSource.SCHEDULED, true);
        }

        log.info("Completed scheduled check for {} stocks", enabledStocks.size());
    }
}
```

---

## Task 7: Controller 层

> **前置条件:** Task 6 已完成

**Files:**
- Create: `src/main/java/com/example/myapi/controller/StockMonitorController.java`
- Create: `src/main/java/com/example/myapi/controller/StockSignalController.java`

---

### Task 7.1: StockMonitorController

- [ ] **Step 1: Create controller**

```java
// src/main/java/com/example/myapi/controller/StockMonitorController.java
package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.StockMonitorDTO.*;
import com.example.myapi.service.StockMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-monitor")
@RequiredArgsConstructor
public class StockMonitorController {

    private final StockMonitorService stockMonitorService;

    // ========== Stock Watch CRUD ==========

    @GetMapping("/stocks")
    public ApiResponse<List<StockWatchResponse>> listStocks() {
        return ApiResponse.ok(stockMonitorService.listStocks());
    }

    @PostMapping("/stocks")
    public ApiResponse<StockWatchResponse> addStock(@RequestBody AddStockRequest request) {
        return ApiResponse.ok(stockMonitorService.addStock(request.getStockCode(), request.getStockName()));
    }

    @PutMapping("/stocks/{id}")
    public ApiResponse<StockWatchResponse> updateStock(
            @PathVariable Long id,
            @RequestBody UpdateStockRequest request) {
        return ApiResponse.ok(stockMonitorService.updateStock(id, request));
    }

    @DeleteMapping("/stocks/{id}")
    public ApiResponse<Void> deleteStock(@PathVariable Long id) {
        stockMonitorService.deleteStock(id);
        return ApiResponse.ok(null);
    }

    // ========== Manual Check ==========

    @PostMapping("/stocks/{stockCode}/check")
    public ApiResponse<CheckResult> checkStock(@PathVariable String stockCode) {
        return ApiResponse.ok(stockMonitorService.checkStock(stockCode,
                com.example.myapi.entity.StockSignalLog.TriggerSource.MANUAL, false));
    }

    // ========== Stock Detail ==========

    @GetMapping("/stocks/{stockCode}/detail")
    public ApiResponse<StockDetailResponse> getStockDetail(@PathVariable String stockCode) {
        return ApiResponse.ok(stockMonitorService.getStockDetail(stockCode));
    }

    // ========== Config ==========

    @GetMapping("/config")
    public ApiResponse<ConfigResponse> getConfig() {
        return ApiResponse.ok(stockMonitorService.getConfig());
    }

    @PutMapping("/config")
    public ApiResponse<ConfigResponse> updateConfig(@RequestBody UpdateConfigRequest request) {
        return ApiResponse.ok(stockMonitorService.updateConfig(request));
    }
}
```

---

### Task 7.2: StockSignalController (for retry notifications)

- [ ] **Step 1: Create controller**

```java
// src/main/java/com/example/myapi/controller/StockSignalController.java
package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.StockMonitorDTO.*;
import com.example.myapi.entity.NotificationStatus;
import com.example.myapi.entity.StockSignalLog;
import com.example.myapi.repository.StockSignalLogRepository;
import com.example.myapi.service.WeChatNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stock-monitor/signals")
@RequiredArgsConstructor
public class StockSignalController {

    private final StockSignalLogRepository signalLogRepository;
    private final WeChatNotificationService weChatService;

    @GetMapping("/pending")
    public ApiResponse<List<SignalLogResponse>> getPendingSignals() {
        List<StockSignalLog> pending = signalLogRepository.findByNotificationStatus(NotificationStatus.NOT_SENT);
        return ApiResponse.ok(pending.stream()
                .map(SignalLogResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/failed")
    public ApiResponse<List<SignalLogResponse>> getFailedSignals() {
        List<StockSignalLog> failed = signalLogRepository.findByNotificationStatus(NotificationStatus.FAILED);
        return ApiResponse.ok(failed.stream()
                .map(SignalLogResponse::from)
                .collect(Collectors.toList()));
    }

    @PostMapping("/{id}/retry")
    @Transactional
    public ApiResponse<String> retryNotification(@PathVariable Long id) {
        StockSignalLog signalLog = signalLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("信号记录不存在"));

        WeChatNotificationService.SendResult result = weChatService.sendSignalNotification(
                signalLog.getStockCode(),
                null,
                signalLog.getSignalType(),
                signalLog.getClosePrice().toString()
        );

        if (result.isSuccess()) {
            signalLog.setNotificationStatus(NotificationStatus.SENT);
            signalLog.setNotificationSentAt(LocalDateTime.now());
            signalLog.setNotificationAttempts(signalLog.getNotificationAttempts() + 1);
            signalLogRepository.save(signalLog);
            return ApiResponse.ok("发送成功");
        } else {
            signalLog.setNotificationStatus(NotificationStatus.FAILED);
            signalLog.setNotificationFailureReason(result.getErrorMessage());
            signalLog.setNotificationAttempts(signalLog.getNotificationAttempts() + 1);
            signalLogRepository.save(signalLog);
            return ApiResponse.ok("发送失败: " + result.getErrorMessage());
        }
    }
}
```

---

## Task 8: 定时任务

> **前置条件:** Task 7 已完成

**Files:**
- Create: `src/main/java/com/example/myapi/scheduler/StockMonitorTask.java`

---

### Task 8.1: StockMonitorTask

- [ ] **Step 1: Create scheduled task**

```java
// src/main/java/com/example/myapi/scheduler/StockMonitorTask.java
package com.example.myapi.scheduler;

import com.example.myapi.service.StockMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockMonitorTask {

    private final StockMonitorService stockMonitorService;

    /**
     * 收盘后执行股票监控检查 (每个交易日 17:10)
     * 只在工作日执行
     */
    @Scheduled(cron = "0 10 17 * * MON-FRI", zone = "Asia/Shanghai")
    public void onMarketClose() {
        log.info("Stock monitor scheduled task started");

        try {
            stockMonitorService.checkAllEnabledStocks();
            log.info("Stock monitor scheduled task completed");
        } catch (Exception e) {
            log.error("Stock monitor scheduled task failed", e);
        }
    }
}
```

---

## Task 9: 前端实现

> **前置条件:** Task 7 已完成

**Files:**
- Create: `frontend/src/types/stockMonitor.ts`
- Create: `frontend/src/api/stockMonitor.ts`
- Create: `frontend/src/hooks/useStockMonitor.ts`
- Create: `frontend/src/pages/StockMonitorList.tsx`
- Create: `frontend/src/pages/StockMonitorDetail.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/layout/AppLayout.tsx`

---

### Task 9.1: 前端类型定义

- [ ] **Step 1: Create types**

```typescript
// frontend/src/types/stockMonitor.ts
export interface StockWatch {
  id: number;
  stockCode: string;
  stockName: string;
  enabled: boolean;
  signalState: 'NONE' | 'WATCHING' | 'J_BOTTOM' | 'J_BOTTOM_2' | 'GOLDEN_CROSS' | 'DEAD_CROSS';
  watchStartDate: string;
  lastSignalType: string | null;
  lastSignalDate: string | null;
  lastCheckedAt: string | null;
  lastCheckStatus: string | null;
  lastCheckMessage: string | null;
}

export interface AddStockRequest {
  stockCode: string;
  stockName: string;
}

export interface UpdateStockRequest {
  enabled?: boolean;
  stockName?: string;
}

export interface ConfigResponse {
  xueqiuConfigured: boolean;
  wechatConfigured: boolean;
  wechatAppIdMasked: string | null;
  wechatTemplateId: string | null;
  updatedAt: string | null;
}

export interface UpdateConfigRequest {
  xueqiuCookie?: string;
  wechatAppId?: string;
  wechatAppSecret?: string;
  wechatTemplateId?: string;
  wechatOpenId?: string;
}

export interface CheckResult {
  stockCode: string;
  stockName: string;
  success: boolean;
  message: string;
  signalState: string | null;
  signalType: string | null;
  closePrice: number | null;
  jValue: number | null;
  macd: number | null;
  notificationSent: boolean | null;
}

export interface SignalLog {
  id: number;
  stockCode: string;
  tradeDate: string;
  signalType: string;
  triggerSource: string;
  closePrice: number;
  jToday: number | null;
  jYesterday: number | null;
  macdToday: number | null;
  macdYesterday: number | null;
  evaluationSummary: string;
  notificationStatus: string;
  notificationSentAt: string | null;
  notificationFailureReason: string | null;
  createdAt: string;
}

export interface IndicatorSnapshot {
  tradeDate: string;
  closePrice: number;
  kdjk: number | null;
  kdjd: number | null;
  kdjj: number | null;
  dif: number | null;
  dea: number | null;
  macd: number | null;
}

export interface StockDetail {
  watch: StockWatch;
  snapshots: IndicatorSnapshot[];
  signalLogs: SignalLog[];
}
```

---

### Task 9.2: API 客户端

- [ ] **Step 1: Create API client**

```typescript
// frontend/src/api/stockMonitor.ts
import client from './client';
import type {
  StockWatch,
  AddStockRequest,
  UpdateStockRequest,
  ConfigResponse,
  UpdateConfigRequest,
  CheckResult,
  StockDetail,
  SignalLog,
} from '../types/stockMonitor';

export const stockMonitorApi = {
  // Stock watch CRUD
  listStocks: () => client.get<StockWatch[]>('/stock-monitor/stocks'),

  addStock: (data: AddStockRequest) =>
    client.post<StockWatch>('/stock-monitor/stocks', data),

  updateStock: (id: number, data: UpdateStockRequest) =>
    client.put<StockWatch>(`/stock-monitor/stocks/${id}`, data),

  deleteStock: (id: number) =>
    client.delete(`/stock-monitor/stocks/${id}`),

  // Manual check
  checkStock: (stockCode: string) =>
    client.post<CheckResult>(`/stock-monitor/stocks/${stockCode}/check`, {}),

  // Stock detail
  getStockDetail: (stockCode: string) =>
    client.get<StockDetail>(`/stock-monitor/stocks/${stockCode}/detail`),

  // Config
  getConfig: () => client.get<ConfigResponse>('/stock-monitor/config'),

  updateConfig: (data: UpdateConfigRequest) =>
    client.put<ConfigResponse>('/stock-monitor/config', data),

  // Signals
  getPendingSignals: () =>
    client.get<SignalLog[]>('/stock-monitor/signals/pending'),

  getFailedSignals: () =>
    client.get<SignalLog[]>('/stock-monitor/signals/failed'),

  retryNotification: (id: number) =>
    client.post(`/stock-monitor/signals/${id}/retry`, {}),
};
```

---

### Task 9.3: React Query Hooks

- [ ] **Step 1: Create hooks**

```typescript
// frontend/src/hooks/useStockMonitor.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { stockMonitorApi } from '../api/stockMonitor';
import type {
  AddStockRequest,
  UpdateStockRequest,
  UpdateConfigRequest,
} from '../types/stockMonitor';

export const STOCK_MONITOR_KEYS = {
  all: ['stock-monitor'] as const,
  stocks: () => [...STOCK_MONITOR_KEYS.all, 'stocks'] as const,
  config: () => [...STOCK_MONITOR_KEYS.all, 'config'] as const,
  detail: (code: string) => [...STOCK_MONITOR_KEYS.all, 'detail', code] as const,
  pendingSignals: () => [...STOCK_MONITOR_KEYS.all, 'pending-signals'] as const,
  failedSignals: () => [...STOCK_MONITOR_KEYS.all, 'failed-signals'] as const,
};

export function useStockMonitorList() {
  return useQuery({
    queryKey: STOCK_MONITOR_KEYS.stocks(),
    queryFn: stockMonitorApi.listStocks,
  });
}

export function useStockMonitorConfig() {
  return useQuery({
    queryKey: STOCK_MONITOR_KEYS.config(),
    queryFn: stockMonitorApi.getConfig,
  });
}

export function useStockMonitorDetail(code: string) {
  return useQuery({
    queryKey: STOCK_MONITOR_KEYS.detail(code),
    queryFn: () => stockMonitorApi.getStockDetail(code),
    enabled: !!code,
  });
}

export function useAddStock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: AddStockRequest) => stockMonitorApi.addStock(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: STOCK_MONITOR_KEYS.stocks() });
    },
  });
}

export function useUpdateStock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateStockRequest }) =>
      stockMonitorApi.updateStock(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: STOCK_MONITOR_KEYS.stocks() });
    },
  });
}

export function useDeleteStock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => stockMonitorApi.deleteStock(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: STOCK_MONITOR_KEYS.stocks() });
    },
  });
}

export function useCheckStock() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stockCode: string) => stockMonitorApi.checkStock(stockCode),
    onSuccess: (_, code) => {
      queryClient.invalidateQueries({ queryKey: STOCK_MONITOR_KEYS.detail(code) });
      queryClient.invalidateQueries({ queryKey: STOCK_MONITOR_KEYS.stocks() });
    },
  });
}

export function useUpdateStockMonitorConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateConfigRequest) => stockMonitorApi.updateConfig(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: STOCK_MONITOR_KEYS.config() });
    },
  });
}

export function useRetryNotification() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => stockMonitorApi.retryNotification(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: STOCK_MONITOR_KEYS.pendingSignals() });
      queryClient.invalidateQueries({ queryKey: STOCK_MONITOR_KEYS.failedSignals() });
    },
  });
}
```

---

### Task 9.4: 导航菜单更新

- [ ] **Step 1: Update AppLayout**

```tsx
// frontend/src/components/layout/AppLayout.tsx
import { NavLink, Outlet } from 'react-router-dom'

const navItems = [
  { to: '/', label: '对比分析' },
  { to: '/plans', label: '预案管理' },
  { to: '/actual-trades', label: '实盘记录' },
  { to: '/snapshots', label: '历史快照' },
  { to: '/stock-monitor', label: '技术指标监控' },
  { to: '/settings', label: '系统设置' },
]

export default function AppLayout() {
  return (
    <div className="flex min-h-screen bg-gray-950 text-gray-100 font-sans">
      <aside className="w-52 flex-shrink-0 border-r border-gray-800 flex flex-col">
        <div className="h-14 flex items-center px-4 border-b border-gray-800">
          <h1 className="text-sm font-medium text-gray-200">预案与实盘对比</h1>
        </div>
        <nav className="flex-1 py-2">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center h-10 px-4 text-sm transition-colors ${
                  isActive
                    ? 'text-blue-400 bg-blue-950 border-r-2 border-blue-400'
                    : 'text-gray-400 hover:text-gray-200 hover:bg-gray-900'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="flex-1 flex flex-col min-w-0">
        <Outlet />
      </main>
    </div>
  )
}
```

---

### Task 9.5: 路由配置

- [ ] **Step 1: Update App.tsx**

```tsx
// frontend/src/App.tsx
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import AppLayout from '@/components/layout/AppLayout'
import Dashboard from '@/pages/Dashboard'
import PlanList from '@/pages/PlanList'
import PlanCreate from '@/pages/PlanCreate'
import PlanDetail from '@/pages/PlanDetail'
import PlanEdit from '@/pages/PlanEdit'
import TradeList from '@/pages/TradeList'
import TradeCreate from '@/pages/TradeCreate'
import TradeEdit from '@/pages/TradeEdit'
import SnapshotList from '@/pages/SnapshotList'
import StockMonitorList from '@/pages/StockMonitorList'
import StockMonitorDetail from '@/pages/StockMonitorDetail'
import Settings from '@/pages/Settings'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<Dashboard />} />
          <Route path="plans" element={<PlanList />} />
          <Route path="plans/new" element={<PlanCreate />} />
          <Route path="plans/sell/new" element={<PlanCreate />} />
          <Route path="plans/:id" element={<PlanDetail />} />
          <Route path="plans/:id/edit" element={<PlanEdit />} />
          <Route path="actual-trades" element={<TradeList />} />
          <Route path="actual-trades/new" element={<TradeCreate />} />
          <Route path="actual-trades/:id/edit" element={<TradeEdit />} />
          <Route path="snapshots" element={<SnapshotList />} />
          <Route path="stock-monitor" element={<StockMonitorList />} />
          <Route path="stock-monitor/:code" element={<StockMonitorDetail />} />
          <Route path="settings" element={<Settings />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
```

---

### Task 9.6: 监控列表页面

- [ ] **Step 1: Create StockMonitorList page**

```tsx
// frontend/src/pages/StockMonitorList.tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useStockMonitorList, useAddStock, useCheckStock, useDeleteStock } from '@/hooks/useStockMonitor'

const signalStateLabels: Record<string, { label: string; color: string }> = {
  NONE: { label: '无', color: 'text-gray-500' },
  WATCHING: { label: '观察中', color: 'text-yellow-500' },
  J_BOTTOM: { label: 'J值触底', color: 'text-orange-500' },
  J_BOTTOM_2: { label: 'J值二次触底', color: 'text-orange-600' },
  GOLDEN_CROSS: { label: '买入信号', color: 'text-green-500' },
  DEAD_CROSS: { label: '卖出信号', color: 'text-red-500' },
}

export default function StockMonitorList() {
  const navigate = useNavigate()
  const { data: stocks, isLoading } = useStockMonitorList()
  const addStock = useAddStock()
  const checkStock = useCheckStock()
  const deleteStock = useDeleteStock()

  const [newCode, setNewCode] = useState('')
  const [newName, setNewName] = useState('')
  const [showAddForm, setShowAddForm] = useState(false)

  const handleAddStock = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newCode.trim()) return

    try {
      await addStock.mutateAsync({
        stockCode: newCode.trim(),
        stockName: newName.trim(),
      })
      setNewCode('')
      setNewName('')
      setShowAddForm(false)
    } catch (err) {
      alert(err instanceof Error ? err.message : '添加失败')
    }
  }

  const handleCheck = async (stockCode: string) => {
    try {
      const result = await checkStock.mutateAsync(stockCode)
      alert(result.data.message)
    } catch (err) {
      alert(err instanceof Error ? err.message : '检查失败')
    }
  }

  const handleDelete = async (id: number, code: string) => {
    if (!confirm(`确定删除 ${code} 吗？`)) return
    try {
      await deleteStock.mutateAsync(id)
    } catch (err) {
      alert(err instanceof Error ? err.message : '删除失败')
    }
  }

  if (isLoading) {
    return <div className="p-6">加载中...</div>
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-medium text-gray-200">技术指标监控</h2>
        <button
          onClick={() => setShowAddForm(!showAddForm)}
          className="px-4 py-2 bg-blue-600 text-white text-sm rounded-md hover:bg-blue-700"
        >
          {showAddForm ? '取消' : '+ 添加股票'}
        </button>
      </div>

      {showAddForm && (
        <form onSubmit={handleAddStock} className="bg-gray-900 border border-gray-800 rounded-lg p-4 space-y-4">
          <div className="flex gap-4">
            <div>
              <label className="block text-sm text-gray-400 mb-1">股票代码</label>
              <input
                type="text"
                value={newCode}
                onChange={(e) => setNewCode(e.target.value)}
                placeholder="600000"
                className="w-32 px-3 py-2 bg-gray-800 border border-gray-700 rounded-md text-sm"
              />
            </div>
            <div>
              <label className="block text-sm text-gray-400 mb-1">股票名称</label>
              <input
                type="text"
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                placeholder="可选"
                className="w-32 px-3 py-2 bg-gray-800 border border-gray-700 rounded-md text-sm"
              />
            </div>
            <div className="flex items-end">
              <button
                type="submit"
                disabled={addStock.isPending}
                className="px-4 py-2 bg-blue-600 text-white text-sm rounded-md hover:bg-blue-700 disabled:opacity-50"
              >
                {addStock.isPending ? '添加中...' : '添加'}
              </button>
            </div>
          </div>
        </form>
      )}

      <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-800 text-gray-400">
            <tr>
              <th className="text-left px-4 py-3">股票</th>
              <th className="text-left px-4 py-3">状态</th>
              <th className="text-left px-4 py-3">最近信号</th>
              <th className="text-left px-4 py-3">最后检查</th>
              <th className="text-left px-4 py-3">检查结果</th>
              <th className="text-right px-4 py-3">操作</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-800">
            {stocks?.data.map((stock) => {
              const stateInfo = signalStateLabels[stock.signalState] || signalStateLabels.NONE
              return (
                <tr key={stock.id} className="hover:bg-gray-800/50">
                  <td className="px-4 py-3">
                    <button
                      onClick={() => navigate(`/stock-monitor/${stock.stockCode}`)}
                      className="text-left hover:text-blue-400"
                    >
                      <div className="font-medium">{stock.stockName || stock.stockCode}</div>
                      <div className="text-xs text-gray-500">{stock.stockCode}</div>
                    </button>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`font-medium ${stateInfo.color}`}>{stateInfo.label}</span>
                  </td>
                  <td className="px-4 py-3">
                    {stock.lastSignalType ? (
                      <span className={stock.lastSignalType === 'GOLDEN_CROSS' ? 'text-green-500' : 'text-red-500'}>
                        {stock.lastSignalType === 'GOLDEN_CROSS' ? '买入' : '卖出'}
                      </span>
                    ) : (
                      <span className="text-gray-600">-</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-gray-500">
                    {stock.lastCheckedAt ? new Date(stock.lastCheckedAt).toLocaleString('zh-CN') : '-'}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500 max-w-xs truncate">
                    {stock.lastCheckMessage || '-'}
                  </td>
                  <td className="px-4 py-3 text-right space-x-2">
                    <button
                      onClick={() => handleCheck(stock.stockCode)}
                      disabled={checkStock.isPending}
                      className="px-2 py-1 text-xs bg-gray-800 hover:bg-gray-700 rounded"
                    >
                      检查
                    </button>
                    <button
                      onClick={() => handleDelete(stock.id, stock.stockCode)}
                      disabled={deleteStock.isPending}
                      className="px-2 py-1 text-xs bg-red-900/50 hover:bg-red-900 text-red-400 rounded"
                    >
                      删除
                    </button>
                  </td>
                </tr>
              )
            })}
            {(!stocks?.data || stocks.data.length === 0) && (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-gray-500">
                  暂无监控股票
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
```

---

### Task 9.7: 股票详情页面

- [ ] **Step 1: Create StockMonitorDetail page**

```tsx
// frontend/src/pages/StockMonitorDetail.tsx
import { useParams, useNavigate } from 'react-router-dom'
import { useStockMonitorDetail, useCheckStock } from '@/hooks/useStockMonitor'

export default function StockMonitorDetail() {
  const { code } = useParams<{ code: string }>()
  const navigate = useNavigate()
  const { data, isLoading } = useStockMonitorDetail(code!)
  const checkStock = useCheckStock()

  const handleCheck = async () => {
    if (!code) return
    try {
      const result = await checkStock.mutateAsync(code)
      alert(result.data.message)
    } catch (err) {
      alert(err instanceof Error ? err.message : '检查失败')
    }
  }

  if (isLoading) {
    return <div className="p-6">加载中...</div>
  }

  if (!data?.data) {
    return <div className="p-6">股票不存在</div>
  }

  const { watch, snapshots, signalLogs } = data.data

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center gap-4">
        <button onClick={() => navigate('/stock-monitor')} className="text-gray-400 hover:text-white">
          ← 返回
        </button>
        <h2 className="text-base font-medium text-gray-200">
          {watch.stockName || watch.stockCode} ({watch.stockCode})
        </h2>
        <button
          onClick={handleCheck}
          disabled={checkStock.isPending}
          className="px-4 py-2 bg-blue-600 text-white text-sm rounded-md hover:bg-blue-700 disabled:opacity-50"
        >
          {checkStock.isPending ? '检查中...' : '立即检查'}
        </button>
      </div>

      {/* Signal State Card */}
      <div className="bg-gray-900 border border-gray-800 rounded-lg p-5">
        <h3 className="text-sm font-medium text-gray-300 mb-4">信号状态</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div>
            <div className="text-xs text-gray-500 mb-1">当前状态</div>
            <div className="text-lg font-medium text-blue-400">{watch.signalState}</div>
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">最后信号</div>
            <div className="text-lg font-medium">
              {watch.lastSignalType ? (
                <span className={watch.lastSignalType === 'GOLDEN_CROSS' ? 'text-green-500' : 'text-red-500'}>
                  {watch.lastSignalType === 'GOLDEN_CROSS' ? '买入' : '卖出'}
                </span>
              ) : (
                <span className="text-gray-500">-</span>
              )}
            </div>
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">最后检查</div>
            <div className="text-sm text-gray-300">
              {watch.lastCheckedAt ? new Date(watch.lastCheckedAt).toLocaleString('zh-CN') : '-'}
            </div>
          </div>
          <div>
            <div className="text-xs text-gray-500 mb-1">检查状态</div>
            <div className="text-sm text-gray-300">{watch.lastCheckStatus || '-'}</div>
          </div>
        </div>
        {watch.lastCheckMessage && (
          <div className="mt-4 p-3 bg-gray-800 rounded text-sm text-gray-400">
            {watch.lastCheckMessage}
          </div>
        )}
      </div>

      {/* Indicator Snapshots */}
      <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
        <h3 className="text-sm font-medium text-gray-300 px-4 py-3 border-b border-gray-800">近期指标</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead className="bg-gray-800 text-gray-400">
              <tr>
                <th className="text-left px-3 py-2">日期</th>
                <th className="text-right px-3 py-2">收盘价</th>
                <th className="text-right px-3 py-2">K</th>
                <th className="text-right px-3 py-2">D</th>
                <th className="text-right px-3 py-2">J</th>
                <th className="text-right px-3 py-2">DIF</th>
                <th className="text-right px-3 py-2">DEA</th>
                <th className="text-right px-3 py-2">MACD</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-800">
              {snapshots.map((snap) => (
                <tr key={snap.tradeDate} className="hover:bg-gray-800/50">
                  <td className="px-3 py-2">{snap.tradeDate}</td>
                  <td className="px-3 py-2 text-right">{snap.closePrice?.toFixed(2)}</td>
                  <td className="px-3 py-2 text-right">{snap.kdjk?.toFixed(2)}</td>
                  <td className="px-3 py-2 text-right">{snap.kdjd?.toFixed(2)}</td>
                  <td className={`px-3 py-2 text-right ${(snap.kdjj ?? 0) < 20 ? 'text-orange-500 font-medium' : ''}`}>
                    {snap.kdjj?.toFixed(2)}
                  </td>
                  <td className="px-3 py-2 text-right">{snap.dif?.toFixed(4)}</td>
                  <td className="px-3 py-2 text-right">{snap.dea?.toFixed(4)}</td>
                  <td className={`px-3 py-2 text-right ${(snap.macd ?? 0) > 0 ? 'text-green-500' : 'text-red-500'}`}>
                    {snap.macd?.toFixed(4)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Signal Logs */}
      <div className="bg-gray-900 border border-gray-800 rounded-lg overflow-hidden">
        <h3 className="text-sm font-medium text-gray-300 px-4 py-3 border-b border-gray-800">信号历史</h3>
        <div className="divide-y divide-gray-800">
          {signalLogs.map((log) => (
            <div key={log.id} className="p-4">
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center gap-3">
                  <span className={`px-2 py-0.5 text-xs rounded ${
                    log.signalType === 'GOLDEN_CROSS'
                      ? 'bg-green-900/50 text-green-400'
                      : log.signalType === 'DEAD_CROSS'
                      ? 'bg-red-900/50 text-red-400'
                      : 'bg-gray-800 text-gray-400'
                  }`}>
                    {log.signalType === 'GOLDEN_CROSS' ? '买入' : log.signalType === 'DEAD_CROSS' ? '卖出' : log.signalType}
                  </span>
                  <span className="text-xs text-gray-500">{log.tradeDate}</span>
                  <span className="text-xs text-gray-600">{log.triggerSource}</span>
                </div>
                <span className={`text-xs ${
                  log.notificationStatus === 'SENT' ? 'text-green-500' :
                  log.notificationStatus === 'FAILED' ? 'text-red-500' : 'text-gray-500'
                }`}>
                  {log.notificationStatus === 'SENT' ? '已通知' :
                   log.notificationStatus === 'FAILED' ? '通知失败' : '未通知'}
                </span>
              </div>
              <div className="text-xs text-gray-500 mb-1">
                J: {log.jToday?.toFixed(2)} | MACD: {log.macdToday?.toFixed(4)} | 收盘价: {log.closePrice}
              </div>
              <div className="text-sm text-gray-400">{log.evaluationSummary}</div>
            </div>
          ))}
          {signalLogs.length === 0 && (
            <div className="p-4 text-center text-gray-500 text-sm">暂无信号记录</div>
          )}
        </div>
      </div>
    </div>
  )
}
```

---

## 实现计划自检

### 1. 规格覆盖检查

| 规格要求 | 对应实现 |
|----------|----------|
| 雪球Cookie配置 | StockMonitorConfig + updateConfig API |
| 微信测试号配置 | StockMonitorConfig + updateConfig API |
| 监控股票CRUD | StockMonitorController + StockWatch实体 |
| KDJ/MACD信号评估 | StockSignalService.evaluate() |
| 信号状态机 | SignalState枚举 + StockSignalService |
| 微信通知 | WeChatNotificationService |
| 收盘后定时检查 | StockMonitorTask (17:10) |
| 手动检查功能 | checkStock API |
| 股票详情页 | StockMonitorDetail页面 |
| 导航菜单 | AppLayout更新 |

### 2. 占位符检查
- ✅ 无 "TBD"、"TODO" 占位符
- ✅ 所有步骤都有实际代码
- ✅ 所有API端点都定义完整

### 3. 类型一致性检查
- ✅ SignalState 枚举值在前后端一致
- ✅ NotificationStatus 枚举值在前后端一致
- ✅ DTO 字段名称与实体一致

### 4. 依赖关系
- Task 3 (XueqiuService) 依赖 Task 2 (Repository)
- Task 4 (StockSignalService) 独立，无外部依赖
- Task 5 (WeChatNotificationService) 依赖 Task 2 (Repository)
- Task 6 (StockMonitorService) 依赖 Task 3, 4, 5
- Task 7 (Controller) 依赖 Task 6
- Task 8 (Scheduler) 依赖 Task 6
- Task 9 (前端) 依赖 Task 7

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-05-31-stock-indicator-monitor.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
