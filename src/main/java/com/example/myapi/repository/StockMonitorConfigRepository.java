package com.example.myapi.repository;

import com.example.myapi.entity.StockMonitorConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockMonitorConfigRepository extends JpaRepository<StockMonitorConfig, Long> {

    /**
     * Get the singleton config row.
     */
    default StockMonitorConfig getConfig() {
        return findAll().stream().findFirst().orElseGet(() -> {
            StockMonitorConfig config = StockMonitorConfig.builder()
                    .xueqiuConfigured(false)
                    .wechatConfigured(false)
                    .build();
            return save(config);
        });
    }
}
