package com.example.myapi.controller;

import com.example.myapi.service.MigrationService;
import com.example.myapi.service.MigrationService.MigrationResult;
import com.example.myapi.service.MigrationService.MigrationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin controller for data migration operations.
 * 
 * WARNING: These endpoints should be protected in production!
 * 
 * Endpoints:
 * - GET  /api/admin/migration/status  - Get current migration status
 * - POST /api/admin/migration/run    - Run migration (backup + DDL + migrate)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final MigrationService migrationService;

    /**
     * Get current migration status.
     */
    @GetMapping("/status")
    public ResponseEntity<MigrationStatus> getStatus() {
        MigrationStatus status = migrationService.getStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Run the complete migration process.
     * 
     * WARNING: This will modify the database schema and data!
     * 
     * Steps:
     * 1. Backup all data to JSON files
     * 2. Apply DDL changes (add/remove columns)
     * 3. Migrate existing plans to new schema
     * 4. Validate migration result
     */
    @PostMapping("/run")
    public ResponseEntity<MigrationResult> runMigration() {
        log.info("Migration endpoint called");
        
        try {
            MigrationResult result = migrationService.runMigration();
            
            if (result.success() && result.isValid()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            log.error("Migration failed", e);
            return ResponseEntity.internalServerError()
                .body(new MigrationResult(
                    false, null, null, null, 0, false,
                    java.util.List.of("Migration failed: " + e.getMessage()),
                    0
                ));
        }
    }

    /**
     * Simple health check for migration endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
