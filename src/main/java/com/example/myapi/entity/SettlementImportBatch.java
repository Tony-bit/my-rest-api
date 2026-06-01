package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_import_batch", indexes = {
        @Index(name = "idx_sib_file_hash", columnList = "file_hash"),
        @Index(name = "idx_sib_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Lob
    @Column(name = "raw_content", columnDefinition = "LONGTEXT")
    private String rawContent;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "archived_rows", nullable = false)
    private int archivedRows;

    @Column(name = "imported_trades", nullable = false)
    private int importedTrades;

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "PROCESSING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
