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

    @Query("SELECT s FROM StockIndicatorSnapshot s WHERE s.stockCode = :stockCode ORDER BY s.tradeDate DESC LIMIT :limit")
    List<StockIndicatorSnapshot> findLatestByStockCode(@Param("stockCode") String stockCode, @Param("limit") int limit);

    List<StockIndicatorSnapshot> findByTradeDate(LocalDate tradeDate);
}
