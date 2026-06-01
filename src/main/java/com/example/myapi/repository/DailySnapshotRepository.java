package com.example.myapi.repository;

import com.example.myapi.entity.DailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailySnapshotRepository extends JpaRepository<DailySnapshot, Long> {

    List<DailySnapshot> findBySnapshotDate(LocalDate snapshotDate);

    List<DailySnapshot> findByPlanId(Long planId);

    List<DailySnapshot> findByPlanIdOrderBySnapshotDateDesc(Long planId);

    @Modifying
    @Query("DELETE FROM DailySnapshot d WHERE d.snapshotDate < :cutoffDate")
    int deleteBySnapshotDateBefore(@Param("cutoffDate") LocalDate cutoffDate);

    void deleteByPlanIdAndSnapshotDate(Long planId, LocalDate snapshotDate);

    void deleteByActualTradeIdAndSnapshotDate(Long actualTradeId, LocalDate snapshotDate);

    @Modifying
    @Query("DELETE FROM DailySnapshot d WHERE d.actualTradeId IS NOT NULL")
    int deleteAllActualTradeSnapshots();

    @Modifying
    @Query("DELETE FROM DailySnapshot d WHERE d.actualTradeId = :actualTradeId")
    int deleteByActualTradeId(@Param("actualTradeId") Long actualTradeId);
}
