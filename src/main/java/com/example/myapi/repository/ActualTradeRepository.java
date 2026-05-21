package com.example.myapi.repository;

import com.example.myapi.entity.ActualTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface ActualTradeRepository extends JpaRepository<ActualTrade, Long> {

    List<ActualTrade> findByStockCode(String stockCode);

    List<ActualTrade> findByTradeDateBetween(LocalDate start, LocalDate end);

    List<ActualTrade> findByStockCodeAndTradeDateBetween(String stockCode, LocalDate start, LocalDate end);

    @Query("SELECT a FROM ActualTrade a WHERE a.stockCode = :stockCode AND a.direction = 'BUY' AND a.isMatched = false ORDER BY a.tradeDate ASC")
    List<ActualTrade> findUnmatchedBuys(@Param("stockCode") String stockCode);

    @Query("SELECT a FROM ActualTrade a WHERE a.stockCode = :stockCode AND a.direction = 'SELL' ORDER BY a.tradeDate ASC")
    List<ActualTrade> findSells(@Param("stockCode") String stockCode);
}
