package com.example.myapi.repository;

import com.example.myapi.entity.StockWatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockWatchRepository extends JpaRepository<StockWatch, Long> {

    Optional<StockWatch> findByStockCode(String stockCode);

    List<StockWatch> findByEnabledTrue();

    boolean existsByStockCode(String stockCode);
}
