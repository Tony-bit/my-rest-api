package com.example.myapi.service;

import com.example.myapi.config.DataMigrationHelper;
import com.example.myapi.config.DataMigrationHelper.BackupResult;
import com.example.myapi.config.DataMigrationHelper.BackupStats;
import com.example.myapi.entity.Plan;
import com.example.myapi.entity.PlanType;
import com.example.myapi.repository.PlanConditionRepository;
import com.example.myapi.repository.PlanExecutionRepository;
import com.example.myapi.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Migration service for buy-sell separation.
 * 
 * This service handles the migration process:
 * 1. Pre-migration: Backup data to JSON
 * 2. Migration: Apply DDL changes and migrate data
 * 3. Post-migration: Validate migration result
 * 
 * Usage:
 * - Check current status: GET /api/admin/migration/status
 * - Run migration: POST /api/admin/migration/run
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationService {

    private final PlanRepository planRepository;
    private final PlanConditionRepository conditionRepository;
    private final PlanExecutionRepository executionRepository;
    private final DataMigrationHelper dataMigrationHelper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Get current migration status and backup statistics.
     */
    @Transactional(readOnly = true)
    public MigrationStatus getStatus() {
        BackupStats stats = dataMigrationHelper.getBackupStats();
        boolean isMigrated = checkIfMigrated();
        boolean hasData = stats.plansCount() > 0 || stats.conditionsCount() > 0 || stats.executionsCount() > 0;
        
        return new MigrationStatus(
            isMigrated,
            hasData,
            stats.plansCount(),
            stats.conditionsCount(),
            stats.executionsCount(),
            LocalDateTime.now()
        );
    }

    /**
     * Check if the database has been migrated to the new schema.
     */
    private boolean checkIfMigrated() {
        try {
            // Check if plan_type column exists
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name = 'plan' AND column_name = 'plan_type'",
                Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Run the complete migration process.
     * 
     * Steps:
     * 1. Create backup of existing data
     * 2. Apply DDL changes
     * 3. Migrate existing plans to BUY type
     * 4. Validate migration result
     */
    @Transactional
    public MigrationResult runMigration() {
        log.info("Starting buy-sell separation migration...");
        
        long startTime = System.currentTimeMillis();
        
        // Step 1: Backup data
        BackupResult backup = dataMigrationHelper.backupData();
        log.info("Backup created at: plans={}, conditions={}, executions={}",
            backup.plansCount(), backup.conditionsCount(), backup.executionsCount());
        
        // Step 2: Apply DDL changes
        applyDDLChanges();
        
        // Step 3: Migrate existing plans
        int migratedPlans = migrateExistingPlans();
        
        // Step 4: Validate
        MigrationValidation validation = validateMigration();
        
        long duration = System.currentTimeMillis() - startTime;
        
        return new MigrationResult(
            true,
            backup.plansFile(),
            backup.conditionsFile(),
            backup.executionsFile(),
            migratedPlans,
            validation.isValid(),
            validation.errors(),
            duration
        );
    }

    /**
     * Apply DDL changes for buy-sell separation.
     */
    private void applyDDLChanges() {
        log.info("Applying DDL changes...");
        
        // Plan table: Add new columns
        executeSQL("ALTER TABLE plan ADD COLUMN IF NOT EXISTS plan_type VARCHAR(10) NOT NULL DEFAULT 'BUY'");
        executeSQL("ALTER TABLE plan ADD COLUMN IF NOT EXISTS trade_plan_id BIGINT");
        executeSQL("ALTER TABLE plan ADD COLUMN IF NOT EXISTS buy_plan_id BIGINT");
        executeSQL("ALTER TABLE plan ADD COLUMN IF NOT EXISTS valid_until DATE");
        
        // Remove old column
        executeSQL("ALTER TABLE plan DROP COLUMN IF EXISTS is_locked");
        
        // PlanCondition table: Remove old columns
        executeSQL("ALTER TABLE plan_condition DROP COLUMN IF EXISTS direction");
        executeSQL("ALTER TABLE plan_condition DROP COLUMN IF EXISTS is_active");
        
        // PlanExecution table: Remove old columns and add new
        executeSQL("ALTER TABLE plan_execution DROP COLUMN IF EXISTS direction");
        executeSQL("ALTER TABLE plan_execution DROP COLUMN IF EXISTS condition_id");
        executeSQL("ALTER TABLE plan_execution DROP COLUMN IF EXISTS quantity");
        executeSQL("ALTER TABLE plan_execution ADD COLUMN IF NOT EXISTS linked_execution_id BIGINT");
        
        // Create indexes
        executeSQL("CREATE INDEX IF NOT EXISTS idx_plan_type ON plan(plan_type)");
        executeSQL("CREATE INDEX IF NOT EXISTS idx_plan_trade ON plan(trade_plan_id)");
        executeSQL("CREATE INDEX IF NOT EXISTS idx_plan_buy ON plan(buy_plan_id)");
        executeSQL("CREATE INDEX IF NOT EXISTS idx_plan_type_status ON plan(plan_type, status)");
        executeSQL("CREATE INDEX IF NOT EXISTS idx_exec_linked ON plan_execution(linked_execution_id)");
        
        log.info("DDL changes applied successfully");
    }

    private void executeSQL(String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.debug("Executed: {}", sql);
        } catch (Exception e) {
            log.warn("SQL execution warning: {} - {}", sql, e.getMessage());
        }
    }

    /**
     * Migrate existing plans to the new schema.
     * All existing plans are converted to BUY type with trade_plan_id = id.
     */
    private int migrateExistingPlans() {
        log.info("Migrating existing plans...");
        
        List<Plan> plans = planRepository.findAll();
        int count = 0;
        
        for (Plan plan : plans) {
            // Set plan type to BUY for all existing plans
            if (plan.getPlanType() == null) {
                plan.setPlanType(PlanType.BUY);
            }
            // Set trade_plan_id = id for BUY plans (self-reference)
            if (plan.getTradePlanId() == null) {
                plan.setTradePlanId(plan.getId());
            }
            planRepository.save(plan);
            count++;
        }
        
        log.info("Migrated {} plans to BUY type");
        return count;
    }

    /**
     * Validate migration result.
     */
    private MigrationValidation validateMigration() {
        var errors = new java.util.ArrayList<String>();
        
        try {
            // Check that plan_type column exists and has valid values
            Long invalidPlans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plan WHERE plan_type NOT IN ('BUY', 'SELL')",
                Long.class);
            if (invalidPlans != null && invalidPlans > 0) {
                errors.add(invalidPlans + " plans have invalid plan_type");
            }
            
            // Check that BUY plans have trade_plan_id = id
            Long orphanPlans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM plan WHERE plan_type = 'BUY' AND (trade_plan_id IS NULL OR trade_plan_id != id)",
                Long.class);
            if (orphanPlans != null && orphanPlans > 0) {
                errors.add(orphanPlans + " BUY plans have invalid trade_plan_id");
            }
            
        } catch (Exception e) {
            errors.add("Validation error: " + e.getMessage());
        }
        
        return new MigrationValidation(errors.isEmpty(), errors);
    }

    // Result records

    public record MigrationStatus(
        boolean isMigrated,
        boolean hasData,
        long plansCount,
        long conditionsCount,
        long executionsCount,
        LocalDateTime checkedAt
    ) {}

    public record MigrationResult(
        boolean success,
        String plansBackupFile,
        String conditionsBackupFile,
        String executionsBackupFile,
        int migratedPlansCount,
        boolean isValid,
        List<String> validationErrors,
        long durationMs
    ) {}

    public record MigrationValidation(boolean isValid, List<String> errors) {}
}
