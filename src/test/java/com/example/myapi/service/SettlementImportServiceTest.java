package com.example.myapi.service;

import com.example.myapi.dto.SettlementImportDTO;
import com.example.myapi.entity.ActualTrade;
import com.example.myapi.entity.SettlementImportBatch;
import com.example.myapi.entity.SettlementRecord;
import com.example.myapi.repository.ActualTradeRepository;
import com.example.myapi.repository.SettlementImportBatchRepository;
import com.example.myapi.repository.SettlementRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementImportServiceTest {

    @Mock
    private ActualTradeRepository tradeRepository;

    @Mock
    private SettlementImportBatchRepository batchRepository;

    @Mock
    private SettlementRecordRepository recordRepository;

    private SettlementImportService service;

    @BeforeEach
    void setUp() {
        ActualTradeService actualTradeService = new ActualTradeService(tradeRepository);
        service = new SettlementImportService(actualTradeService, tradeRepository, batchRepository, recordRepository);

        when(batchRepository.save(any(SettlementImportBatch.class))).thenAnswer(inv -> {
            SettlementImportBatch batch = inv.getArgument(0);
            setField(batch, "id", 10L);
            return batch;
        });
        when(recordRepository.save(any(SettlementRecord.class))).thenAnswer(inv -> {
            SettlementRecord record = inv.getArgument(0);
            if (record.getId() == null) {
                setField(record, "id", 100L);
            }
            return record;
        });
    }

    @Test
    void importDongguanSettlementTxt_archivesRowsAndImportsOnlyStockTrades() {
        when(tradeRepository.existsBySettlementUniqueKey(anyString())).thenReturn(false);
        when(tradeRepository.findUnmatchedBuys(anyString())).thenReturn(List.of());
        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade trade = inv.getArgument(0);
            if (trade.getId() == null) {
                setField(trade, "id", 200L);
            }
            return trade;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "20260525交割清单查询.txt",
                "text/plain",
                settlementText().getBytes(StandardCharsets.UTF_8)
        );

        SettlementImportDTO.ImportResponse response = service.importDongguanSettlement(file);

        assertEquals(10L, response.getBatchId());
        assertEquals(4, response.getArchivedRows());
        assertEquals(2, response.getImportedTrades());
        assertEquals(2, response.getSkippedRows());
        assertEquals(0, response.getDuplicateRows());

        ArgumentCaptor<ActualTrade> tradeCaptor = ArgumentCaptor.forClass(ActualTrade.class);
        verify(tradeRepository, times(2)).save(tradeCaptor.capture());

        ActualTrade buy = tradeCaptor.getAllValues().get(0);
        assertEquals("300750", buy.getStockCode());
        assertEquals("宁德时代", buy.getStockName());
        assertEquals("0260368430", buy.getSettlementAccountNumber());
        assertEquals("证券买入", buy.getSettlementTradeType());
        assertEquals("0260368430|20260522|证券买入|300750|100|412.53|-41258", buy.getSettlementUniqueKey());
        assertEquals("5.00", buy.getTotalFee().toPlainString());

        ActualTrade sell = tradeCaptor.getAllValues().get(1);
        assertEquals("301599", sell.getStockCode());
        assertEquals("证券卖出", sell.getSettlementTradeType());
        assertEquals("23.49", sell.getTotalFee().toPlainString());

        ArgumentCaptor<SettlementImportBatch> batchCaptor = ArgumentCaptor.forClass(SettlementImportBatch.class);
        verify(batchRepository, atLeastOnce()).save(batchCaptor.capture());
        SettlementImportBatch finalBatch = batchCaptor.getAllValues().get(batchCaptor.getAllValues().size() - 1);
        assertEquals("COMPLETED", finalBatch.getStatus());
        assertTrue(finalBatch.getRawContent().contains("宁德时代"));
    }

    @Test
    void importDongguanSettlementTxt_skipsDuplicateSettlementKeys() {
        when(tradeRepository.existsBySettlementUniqueKey(anyString())).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "duplicate.txt",
                "text/plain",
                singleBuyText().getBytes(StandardCharsets.UTF_8)
        );

        SettlementImportDTO.ImportResponse response = service.importDongguanSettlement(file);

        assertEquals(1, response.getArchivedRows());
        assertEquals(0, response.getImportedTrades());
        assertEquals(1, response.getDuplicateRows());
        verify(tradeRepository, never()).save(any(ActualTrade.class));

        ArgumentCaptor<SettlementRecord> recordCaptor = ArgumentCaptor.forClass(SettlementRecord.class);
        verify(recordRepository, atLeastOnce()).save(recordCaptor.capture());
        SettlementRecord finalRecord = recordCaptor.getAllValues().get(recordCaptor.getAllValues().size() - 1);
        assertEquals("DUPLICATE", finalRecord.getImportStatus());
        assertEquals("Duplicate settlement key", finalRecord.getSkipReason());
    }

    private String settlementText() {
        return """
                -------------------------------------------------------------------------------------------------------

                证券代码        证券名称        成交金额           发生金额            印花税         过户费        发生日期        交易类别                币种          发生数量        成交均价        证券账号          佣金          其他费用        备注
                300750          宁德时代        41253.0000         -41258.0000         0.0000         0.4100        20260522        证券买入                人民币        100             412.5300        0260368430        2.3500        2.2400          0
                301599          理奇智能        36985.0000         36961.5100          18.4900        0.3700        20260522        证券卖出                人民币        -500            73.9700         0260368430        2.6300        2.0000          0
                                0.0100             -0.0100             0.0000         0.0000        20260521        港股通组合费收取        人民币        0               0.0000          A643803160        0.0000        0.0000          0
                204001          GC001           194000.0000        -194000.1900        0.0000         0.0000        20260521        质押回购拆出            人民币        1940            1.3400          A643803160        0.1900        0.0000          0
                """;
    }

    private String singleBuyText() {
        return """
                证券代码        证券名称        成交金额           发生金额            印花税         过户费        发生日期        交易类别                币种          发生数量        成交均价        证券账号          佣金          其他费用        备注
                300750          宁德时代        41253.0000         -41258.0000         0.0000         0.4100        20260522        证券买入                人民币        100             412.5300        0260368430        2.3500        2.2400          0
                """;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
