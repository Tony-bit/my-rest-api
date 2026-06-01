package com.example.myapi.repository;

import com.example.myapi.entity.NotificationStatus;
import com.example.myapi.entity.StockSignalLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockSignalLogRepository extends JpaRepository<StockSignalLog, Long> {

    List<StockSignalLog> findByStockCodeOrderByCreatedAtDesc(String stockCode);

    @Query("SELECT s FROM StockSignalLog s WHERE s.stockCode = :stockCode AND s.tradeDate = :tradeDate AND s.signalType = :signalType ORDER BY s.createdAt DESC")
    List<StockSignalLog> findByStockCodeAndTradeDateAndSignalType(
            @Param("stockCode") String stockCode,
            @Param("tradeDate") LocalDate tradeDate,
            @Param("signalType") String signalType);

    boolean existsByStockCodeAndTradeDateAndSignalTypeAndNotificationStatus(
            String stockCode, LocalDate tradeDate, String signalType, NotificationStatus status);

    @Query("SELECT s FROM StockSignalLog s WHERE s.stockCode = :stockCode AND s.tradeDate = :tradeDate AND s.signalType = :signalType AND s.notificationStatus = :status")
    List<StockSignalLog> findPendingByNotificationKey(
            @Param("stockCode") String stockCode,
            @Param("tradeDate") LocalDate tradeDate,
            @Param("signalType") String signalType,
            @Param("status") NotificationStatus status);

    @Modifying
    @Query("UPDATE StockSignalLog s SET s.notificationStatus = :newStatus, s.notificationSentAt = CURRENT_TIMESTAMP WHERE s.stockCode = :stockCode AND s.tradeDate = :tradeDate AND s.signalType = :signalType AND s.id IN :ids")
    int markNotificationsAsSent(
            @Param("stockCode") String stockCode,
            @Param("tradeDate") LocalDate tradeDate,
            @Param("signalType") String signalType,
            @Param("newStatus") NotificationStatus newStatus,
            @Param("ids") List<Long> ids);

    @Query("SELECT DISTINCT new com.example.myapi.repository.SignalGroup(s.stockCode, s.tradeDate, s.signalType) " +
           "FROM StockSignalLog s WHERE s.stockCode = :stockCode ORDER BY s.tradeDate DESC, s.createdAt DESC")
    List<Object> findDistinctSignalGroups(@Param("stockCode") String stockCode);

    @Query("SELECT COUNT(s) FROM StockSignalLog s WHERE s.stockCode = :stockCode AND s.tradeDate = :tradeDate AND s.signalType = :signalType")
    long countByNotificationKey(
            @Param("stockCode") String stockCode,
            @Param("tradeDate") LocalDate tradeDate,
            @Param("signalType") String signalType);
}
