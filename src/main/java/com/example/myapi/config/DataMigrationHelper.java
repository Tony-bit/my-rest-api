package com.example.myapi.config;

import com.example.myapi.entity.Plan;
import com.example.myapi.entity.PlanCondition;
import com.example.myapi.entity.PlanExecution;
import com.example.myapi.repository.PlanConditionRepository;
import com.example.myapi.repository.PlanExecutionRepository;
import com.example.myapi.repository.PlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Data migration helper for buy-sell separation.
 * Provides methods to backup and restore data in JSON format.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataMigrationHelper {

    private final PlanRepository planRepository;
    private final PlanConditionRepository conditionRepository;
    private final PlanExecutionRepository executionRepository;
    private final ObjectMapper objectMapper;

    private static final String BACKUP_DIR = "backups";

    /**
     * Backup all plan-related data to JSON files.
     * Returns the backup file paths for reference.
     */
    @Transactional(readOnly = true)
    public BackupResult backupData() {
        try {
            Path backupDir = createBackupDirectory();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            
            // Backup plans
            Path plansFile = backupDir.resolve("plans_" + timestamp + ".json");
            var plans = planRepository.findAll();
            var planBackup = plans.stream().map(this::toPlanBackup).toList();
            objectMapper.writeValue(plansFile.toFile(), planBackup);
            log.info("Backed up {} plans to {}", plans.size(), plansFile);
            
            // Backup conditions
            Path conditionsFile = backupDir.resolve("conditions_" + timestamp + ".json");
            var conditions = conditionRepository.findAll();
            objectMapper.writeValue(conditionsFile.toFile(), conditions);
            log.info("Backed up {} conditions to {}", conditions.size(), conditionsFile);
            
            // Backup executions
            Path executionsFile = backupDir.resolve("executions_" + timestamp + ".json");
            var executions = executionRepository.findAll();
            objectMapper.writeValue(executionsFile.toFile(), executions);
            log.info("Backed up {} executions to {}", executions.size(), executionsFile);
            
            return new BackupResult(
                plansFile.toString(),
                conditionsFile.toString(),
                executionsFile.toString(),
                plans.size(),
                conditions.size(),
                executions.size()
            );
        } catch (IOException e) {
            log.error("Failed to backup data", e);
            throw new RuntimeException("Data backup failed: " + e.getMessage(), e);
        }
    }

    private Path createBackupDirectory() throws IOException {
        Path backupDir = Paths.get(BACKUP_DIR);
        if (!Files.exists(backupDir)) {
            Files.createDirectories(backupDir);
        }
        return backupDir;
    }

    private PlanBackup toPlanBackup(Plan plan) {
        return new PlanBackup(
            plan.getId(),
            plan.getName(),
            plan.getStockCode(),
            plan.getStockName(),
            plan.getCycle() != null ? plan.getCycle().name() : null,
            plan.getStatus() != null ? plan.getStatus().name() : null,
            plan.getExecutionQuantity(),
            plan.getTriggerDate(),
            plan.getCreatedAt(),
            plan.getUpdatedAt()
        );
    }

    public record BackupResult(
        String plansFile,
        String conditionsFile,
        String executionsFile,
        int plansCount,
        int conditionsCount,
        int executionsCount
    ) {}

    public record PlanBackup(
        Long id,
        String name,
        String stockCode,
        String stockName,
        String cycle,
        String status,
        java.math.BigDecimal executionQuantity,
        java.time.LocalDate triggerDate,
        java.time.LocalDateTime createdAt,
        java.time.LocalDateTime updatedAt
    ) {}

    /**
     * Get backup statistics without creating backup files.
     */
    @Transactional(readOnly = true)
    public BackupStats getBackupStats() {
        long plansCount = planRepository.count();
        long conditionsCount = conditionRepository.count();
        long executionsCount = executionRepository.count();
        return new BackupStats(plansCount, conditionsCount, executionsCount);
    }

    public record BackupStats(long plansCount, long conditionsCount, long executionsCount) {}
}
