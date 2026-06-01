package com.example.myapi.service;

import com.example.myapi.dto.ActualTradeDTO;
import com.example.myapi.dto.SettlementImportDTO;
import com.example.myapi.entity.SettlementImportBatch;
import com.example.myapi.entity.SettlementRecord;
import com.example.myapi.entity.TradeDirection;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.ActualTradeRepository;
import com.example.myapi.repository.SettlementImportBatchRepository;
import com.example.myapi.repository.SettlementRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementImportService {

    private static final String BUY_TYPE = "\u8bc1\u5238\u4e70\u5165";
    private static final String SELL_TYPE = "\u8bc1\u5238\u5356\u51fa";
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final DateTimeFormatter TRADE_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final ActualTradeService actualTradeService;
    private final ActualTradeRepository actualTradeRepository;
    private final SettlementImportBatchRepository batchRepository;
    private final SettlementRecordRepository recordRepository;

    @Transactional
    public SettlementImportDTO.ImportResponse importDongguanSettlement(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Settlement file cannot be empty");
        }

        byte[] bytes = readBytes(file);
        String rawContent = decodeContent(bytes);
        String fileHash = sha256(bytes);

        SettlementImportBatch batch = batchRepository.save(SettlementImportBatch.builder()
                .originalFilename(file.getOriginalFilename())
                .fileHash(fileHash)
                .rawContent(rawContent)
                .status("PROCESSING")
                .build());

        List<SettlementImportDTO.LineResult> lineResults = new ArrayList<>();
        ImportCounter counter = new ImportCounter();

        for (ParsedSettlementRow row : parseRows(rawContent)) {
            SettlementRecord record = recordRepository.save(toRecord(batch, row));
            counter.archivedRows++;

            SettlementImportDTO.LineResult.LineResultBuilder result = SettlementImportDTO.LineResult.builder()
                    .rowNumber(row.rowNumber())
                    .stockCode(row.stockCode())
                    .stockName(row.stockName())
                    .tradeType(row.tradeType());

            if (!isStockTrade(row)) {
                record.setImportStatus("SKIPPED");
                record.setSkipReason("Unsupported settlement trade type");
                recordRepository.save(record);
                counter.skippedRows++;
                lineResults.add(result.status("SKIPPED")
                        .reason(record.getSkipReason())
                        .build());
                continue;
            }

            if (actualTradeRepository.existsBySettlementUniqueKey(row.uniqueKey())) {
                record.setImportStatus("DUPLICATE");
                record.setSkipReason("Duplicate settlement key");
                recordRepository.save(record);
                counter.duplicateRows++;
                lineResults.add(result.status("DUPLICATE")
                        .reason(record.getSkipReason())
                        .build());
                continue;
            }

            try {
                ActualTradeDTO.Response imported = actualTradeService.create(toCreateRequest(row, record.getId()));
                record.setImportStatus("IMPORTED");
                record.setActualTradeId(imported.getId());
                recordRepository.save(record);
                counter.importedTrades++;
                lineResults.add(result.status("IMPORTED")
                        .actualTradeId(imported.getId())
                        .build());
            } catch (RuntimeException ex) {
                record.setImportStatus("FAILED");
                record.setSkipReason(limit(ex.getMessage(), 255));
                recordRepository.save(record);
                counter.failedRows++;
                lineResults.add(result.status("FAILED")
                        .reason(record.getSkipReason())
                        .build());
            }
        }

        batch.setArchivedRows(counter.archivedRows);
        batch.setImportedTrades(counter.importedTrades);
        batch.setSkippedRows(counter.skippedRows);
        batch.setDuplicateRows(counter.duplicateRows);
        batch.setFailedRows(counter.failedRows);
        batch.setStatus(counter.failedRows > 0 ? "COMPLETED_WITH_ERRORS" : "COMPLETED");
        batchRepository.save(batch);

        return SettlementImportDTO.ImportResponse.builder()
                .batchId(batch.getId())
                .filename(batch.getOriginalFilename())
                .fileHash(batch.getFileHash())
                .archivedRows(counter.archivedRows)
                .importedTrades(counter.importedTrades)
                .skippedRows(counter.skippedRows)
                .duplicateRows(counter.duplicateRows)
                .failedRows(counter.failedRows)
                .lineResults(lineResults)
                .build();
    }

    private List<ParsedSettlementRow> parseRows(String rawContent) {
        List<ParsedSettlementRow> rows = new ArrayList<>();
        String[] lines = rawContent.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("-") || trimmed.contains("\u8bc1\u5238\u4ee3\u7801")) {
                continue;
            }

            String[] parts = trimmed.split("\\s{2,}");
            ParsedSettlementRow row = parseLine(i + 1, line, parts);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private ParsedSettlementRow parseLine(int rowNumber, String rawLine, String[] parts) {
        if (parts.length >= 15) {
            return buildRow(rowNumber, rawLine, 0, parts);
        }
        if (parts.length >= 13) {
            String[] expanded = new String[15];
            expanded[0] = "";
            expanded[1] = "";
            System.arraycopy(parts, 0, expanded, 2, 13);
            return buildRow(rowNumber, rawLine, 0, expanded);
        }
        return null;
    }

    private ParsedSettlementRow buildRow(int rowNumber, String rawLine, int offset, String[] parts) {
        BigDecimal turnoverAmount = decimal(parts[offset + 2]);
        BigDecimal settlementAmount = decimal(parts[offset + 3]);
        BigDecimal quantity = decimal(parts[offset + 9]);
        BigDecimal price = decimal(parts[offset + 10]);

        return new ParsedSettlementRow(
                rowNumber,
                rawLine,
                blankToNull(parts[offset]),
                blankToNull(parts[offset + 1]),
                turnoverAmount,
                settlementAmount,
                decimal(parts[offset + 4]),
                decimal(parts[offset + 5]),
                LocalDate.parse(parts[offset + 6], TRADE_DATE_FORMATTER),
                parts[offset + 7],
                parts[offset + 8],
                quantity,
                price,
                parts[offset + 11],
                decimal(parts[offset + 12]),
                decimal(parts[offset + 13]),
                parts[offset + 14],
                uniqueKey(parts[offset + 11], parts[offset + 6], parts[offset + 7],
                        blankToNull(parts[offset]), quantity, price, settlementAmount)
        );
    }

    private SettlementRecord toRecord(SettlementImportBatch batch, ParsedSettlementRow row) {
        return SettlementRecord.builder()
                .batch(batch)
                .rowNumber(row.rowNumber())
                .rawLine(row.rawLine())
                .stockCode(row.stockCode())
                .stockName(row.stockName())
                .turnoverAmount(row.turnoverAmount())
                .settlementAmount(row.settlementAmount())
                .stampTax(row.stampTax())
                .transferFee(row.transferFee())
                .tradeDate(row.tradeDate())
                .tradeType(row.tradeType())
                .currency(row.currency())
                .quantity(row.quantity())
                .price(row.price())
                .accountNumber(row.accountNumber())
                .commission(row.commission())
                .otherFee(row.otherFee())
                .remark(row.remark())
                .settlementUniqueKey(row.uniqueKey())
                .importStatus("ARCHIVED")
                .build();
    }

    private ActualTradeDTO.CreateRequest toCreateRequest(ParsedSettlementRow row, Long recordId) {
        TradeDirection direction = BUY_TYPE.equals(row.tradeType()) ? TradeDirection.BUY : TradeDirection.SELL;
        return ActualTradeDTO.CreateRequest.builder()
                .stockCode(row.stockCode())
                .stockName(row.stockName())
                .direction(direction)
                .price(row.price())
                .quantity(row.quantity().abs())
                .tradeDate(row.tradeDate())
                .turnoverAmount(row.turnoverAmount())
                .settlementAmount(row.settlementAmount())
                .stampTax(row.stampTax())
                .transferFee(row.transferFee())
                .commission(row.commission())
                .otherFee(row.otherFee())
                .totalFee(totalFee(row))
                .settlementAccountNumber(row.accountNumber())
                .settlementTradeType(row.tradeType())
                .settlementUniqueKey(row.uniqueKey())
                .settlementRecordId(recordId)
                .build();
    }

    private boolean isStockTrade(ParsedSettlementRow row) {
        return row.stockCode() != null && (BUY_TYPE.equals(row.tradeType()) || SELL_TYPE.equals(row.tradeType()));
    }

    private BigDecimal totalFee(ParsedSettlementRow row) {
        return row.stampTax()
                .add(row.transferFee())
                .add(row.commission())
                .add(row.otherFee())
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String uniqueKey(String accountNumber, String tradeDate, String tradeType, String stockCode,
                             BigDecimal quantity, BigDecimal price, BigDecimal settlementAmount) {
        return String.join("|",
                accountNumber,
                tradeDate,
                tradeType,
                stockCode == null ? "" : stockCode,
                normalize(quantity),
                normalize(price),
                normalize(settlementAmount)
        );
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value.trim());
    }

    private String normalize(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String blankToNull(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Failed to read settlement file: " + e.getMessage());
        }
    }

    private String decodeContent(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ignored) {
            return GB18030.decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record ParsedSettlementRow(
            int rowNumber,
            String rawLine,
            String stockCode,
            String stockName,
            BigDecimal turnoverAmount,
            BigDecimal settlementAmount,
            BigDecimal stampTax,
            BigDecimal transferFee,
            LocalDate tradeDate,
            String tradeType,
            String currency,
            BigDecimal quantity,
            BigDecimal price,
            String accountNumber,
            BigDecimal commission,
            BigDecimal otherFee,
            String remark,
            String uniqueKey
    ) {
    }

    private static class ImportCounter {
        private int archivedRows;
        private int importedTrades;
        private int skippedRows;
        private int duplicateRows;
        private int failedRows;
    }
}
