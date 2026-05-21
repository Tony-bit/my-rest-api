package com.example.myapi.scheduler;

import com.example.myapi.repository.DailySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotCleanupTask {

    private final DailySnapshotRepository snapshotRepository;

    @Value("${snapshot.retention-months:3}")
    private int retentionMonths;

    @Scheduled(cron = "0 0 3 1 * *", zone = "Asia/Shanghai")
    @Transactional
    public void cleanupOldSnapshots() {
        LocalDate cutoffDate = LocalDate.now().minusMonths(retentionMonths);
        int deleted = snapshotRepository.deleteBySnapshotDateBefore(cutoffDate);
        log.info("Cleaned up {} snapshots older than {}", deleted, cutoffDate);
    }
}
