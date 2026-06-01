package com.example.myapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class SettlementImportDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ImportResponse {
        private Long batchId;
        private String filename;
        private String fileHash;
        private int archivedRows;
        private int importedTrades;
        private int skippedRows;
        private int duplicateRows;
        private int failedRows;
        private List<LineResult> lineResults;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LineResult {
        private int rowNumber;
        private String stockCode;
        private String stockName;
        private String tradeType;
        private String status;
        private String reason;
        private Long actualTradeId;
    }
}
