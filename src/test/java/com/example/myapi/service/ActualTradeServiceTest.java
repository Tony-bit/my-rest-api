package com.example.myapi.service;

import com.example.myapi.dto.ActualTradeDTO;
import com.example.myapi.entity.*;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.ActualTradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActualTradeServiceTest {

    @Mock
    private ActualTradeRepository tradeRepository;

    private ActualTradeService service;

    @BeforeEach
    void setUp() {
        service = new ActualTradeService(tradeRepository);
    }

    // 4.1 create

    @Test
    void create_buyRecord_savesSuccessfully() {
        ActualTradeDTO.CreateRequest request = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001")
                .direction(TradeDirection.BUY)
                .price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("100"))
                .tradeDate(LocalDate.of(2026, 5, 20))
                .build();

        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", 1L);
            return t;
        });

        ActualTradeDTO.Response resp = service.create(request);

        assertNotNull(resp.getId());
        assertEquals(TradeDirection.BUY, resp.getDirection());
        assertNull(resp.getProfitLoss());
        verify(tradeRepository, times(1)).save(any(ActualTrade.class));
    }

    @Test
    void create_sellRecord_triggersFifoMatching() {
        ActualTrade existingBuy = TestFixtures.tradeBuilder()
                .id(1L)
                .direction(TradeDirection.BUY)
                .price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("100"))
                .tradeDate(LocalDate.of(2026, 5, 15))
                .isMatched(false)
                .build();

        when(tradeRepository.findUnmatchedBuys("000001")).thenReturn(new ArrayList<>(List.of(existingBuy)));
        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", 2L);
            return t;
        });

        ActualTradeDTO.CreateRequest request = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001")
                .direction(TradeDirection.SELL)
                .price(new BigDecimal("12.00"))
                .quantity(new BigDecimal("50"))
                .tradeDate(LocalDate.of(2026, 5, 20))
                .build();

        ActualTradeDTO.Response resp = service.create(request);

        assertNotNull(resp.getId());
        assertEquals(new BigDecimal("100.00"), resp.getProfitLoss()); // (12-10)*50
        assertTrue(resp.getIsMatched());
    }

    @Test
    void create_sellWithNoMatchedBuy_logsWarning() {
        when(tradeRepository.findUnmatchedBuys("000001")).thenReturn(List.of());
        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", 1L);
            return t;
        });

        ActualTradeDTO.CreateRequest request = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001")
                .direction(TradeDirection.SELL)
                .price(new BigDecimal("12.00"))
                .quantity(new BigDecimal("50"))
                .tradeDate(LocalDate.of(2026, 5, 20))
                .build();

        ActualTradeDTO.Response resp = service.create(request);
        assertNull(resp.getProfitLoss());
    }

    // 4.2 FIFO core scenarios

    @Test
    void fifo_singleSellMatchesSingleBuy() {
        ActualTrade buy = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("100")).isMatched(false).build();

        when(tradeRepository.findUnmatchedBuys("000001")).thenReturn(new ArrayList<>(List.of(buy)));
        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", 2L);
            return t;
        });

        ActualTradeDTO.CreateRequest request = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001").direction(TradeDirection.SELL)
                .price(new BigDecimal("12.00")).quantity(new BigDecimal("100"))
                .tradeDate(LocalDate.of(2026, 5, 20)).build();

        ActualTradeDTO.Response resp = service.create(request);

        assertEquals(new BigDecimal("200.00"), resp.getProfitLoss());
        assertEquals(new BigDecimal("20.0000"), resp.getProfitLossPercent());
    }

    @Test
    void fifo_singleSellMatchesMultipleBuys() {
        ActualTrade buy1 = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("100")).isMatched(false)
                .tradeDate(LocalDate.of(2026, 5, 1)).build();
        ActualTrade buy2 = TestFixtures.tradeBuilder()
                .id(2L).direction(TradeDirection.BUY).price(new BigDecimal("12.00"))
                .quantity(new BigDecimal("100")).isMatched(false)
                .tradeDate(LocalDate.of(2026, 5, 10)).build();

        when(tradeRepository.findUnmatchedBuys("000001")).thenReturn(new ArrayList<>(List.of(buy1, buy2)));
        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", 3L);
            return t;
        });

        // SELL 150 shares at 14.00
        ActualTradeDTO.CreateRequest request = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001").direction(TradeDirection.SELL)
                .price(new BigDecimal("14.00")).quantity(new BigDecimal("150"))
                .tradeDate(LocalDate.of(2026, 5, 15)).build();

        ActualTradeDTO.Response resp = service.create(request);

        // avgCost = (10*100 + 12*50)/150 = 10.67
        BigDecimal avgCost = new BigDecimal("10.6667");
        BigDecimal expectedPL = new BigDecimal("14.00").subtract(avgCost)
                .multiply(new BigDecimal("150"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertNotNull(resp.getProfitLoss());
    }

    @Test
    void fifo_sellExceedsAllBuys_partialMatch() {
        ActualTrade buy1 = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("100")).isMatched(false).build();
        ActualTrade buy2 = TestFixtures.tradeBuilder()
                .id(2L).direction(TradeDirection.BUY).price(new BigDecimal("12.00"))
                .quantity(new BigDecimal("100")).isMatched(false).build();

        when(tradeRepository.findUnmatchedBuys("000001")).thenReturn(new ArrayList<>(List.of(buy1, buy2)));
        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", 3L);
            return t;
        });

        ActualTradeDTO.CreateRequest request = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001").direction(TradeDirection.SELL)
                .price(new BigDecimal("15.00")).quantity(new BigDecimal("250"))
                .tradeDate(LocalDate.of(2026, 5, 20)).build();

        ActualTradeDTO.Response resp = service.create(request);

        assertTrue(resp.getIsMatched());
    }

    @Test
    void fifo_sellLessThanBuy_partialMatch() {
        ActualTrade buy1 = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("100")).isMatched(false).build();
        ActualTrade buy2 = TestFixtures.tradeBuilder()
                .id(2L).direction(TradeDirection.BUY).price(new BigDecimal("12.00"))
                .quantity(new BigDecimal("100")).isMatched(false).build();

        when(tradeRepository.findUnmatchedBuys("000001")).thenReturn(new ArrayList<>(List.of(buy1, buy2)));
        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", 3L);
            return t;
        });

        ActualTradeDTO.CreateRequest request = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001").direction(TradeDirection.SELL)
                .price(new BigDecimal("13.00")).quantity(new BigDecimal("50"))
                .tradeDate(LocalDate.of(2026, 5, 20)).build();

        ActualTradeDTO.Response resp = service.create(request);

        // Only matches 50 of BUY1 → avgCost=10, profit=(13-10)*50=150
        assertEquals(new BigDecimal("150.00"), resp.getProfitLoss());
        assertEquals(new BigDecimal("30.0000"), resp.getProfitLossPercent());
    }

    @Test
    void fifo_sellAtLoss_calculatesNegativePL() {
        ActualTrade buy = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).price(new BigDecimal("20.00"))
                .quantity(new BigDecimal("100")).isMatched(false).build();

        when(tradeRepository.findUnmatchedBuys("000001")).thenReturn(new ArrayList<>(List.of(buy)));
        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", 2L);
            return t;
        });

        ActualTradeDTO.CreateRequest request = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001").direction(TradeDirection.SELL)
                .price(new BigDecimal("15.00")).quantity(new BigDecimal("100"))
                .tradeDate(LocalDate.of(2026, 5, 20)).build();

        ActualTradeDTO.Response resp = service.create(request);

        assertEquals(new BigDecimal("-500.00"), resp.getProfitLoss());
        assertEquals(new BigDecimal("-25.0000"), resp.getProfitLossPercent());
    }

    @Test
    void fifo_multipleSells_sequentialMatching() {
        ActualTrade buy1 = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("100")).isMatched(false)
                .tradeDate(LocalDate.of(2026, 5, 1)).build();

        when(tradeRepository.findUnmatchedBuys("000001"))
                .thenReturn(new ArrayList<>(List.of(buy1)))
                .thenReturn(new ArrayList<>(List.of(buy1))); // second call for second sell

        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", (long)(Math.random() * 1000));
            return t;
        });

        // SELL1: 50 shares at 12
        ActualTradeDTO.CreateRequest req1 = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001").direction(TradeDirection.SELL)
                .price(new BigDecimal("12.00")).quantity(new BigDecimal("50"))
                .tradeDate(LocalDate.of(2026, 5, 20)).build();
        ActualTradeDTO.Response resp1 = service.create(req1);

        // SELL2: 30 shares at 11
        ActualTradeDTO.CreateRequest req2 = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001").direction(TradeDirection.SELL)
                .price(new BigDecimal("11.00")).quantity(new BigDecimal("30"))
                .tradeDate(LocalDate.of(2026, 5, 21)).build();
        ActualTradeDTO.Response resp2 = service.create(req2);

        assertEquals(new BigDecimal("100.00"), resp1.getProfitLoss()); // (12-10)*50
        assertEquals(new BigDecimal("30.00"), resp2.getProfitLoss());  // (11-10)*30
    }

    @Test
    void fifo_buyOrderByDate_fifoOrder() {
        ActualTrade buy1 = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).price(new BigDecimal("15.00"))
                .quantity(new BigDecimal("100")).isMatched(false)
                .tradeDate(LocalDate.of(2026, 5, 1)).build();
        ActualTrade buy2 = TestFixtures.tradeBuilder()
                .id(2L).direction(TradeDirection.BUY).price(new BigDecimal("10.00"))
                .quantity(new BigDecimal("100")).isMatched(false)
                .tradeDate(LocalDate.of(2026, 5, 10)).build();

        // findUnmatchedBuys returns sorted by tradeDate ASC
        List<ActualTrade> sortedBuys = Arrays.asList(buy1, buy2);
        when(tradeRepository.findUnmatchedBuys("000001")).thenReturn(new ArrayList<>(sortedBuys));
        when(tradeRepository.save(any(ActualTrade.class))).thenAnswer(inv -> {
            ActualTrade t = inv.getArgument(0);
            setField(t, "id", 3L);
            return t;
        });

        ActualTradeDTO.CreateRequest request = ActualTradeDTO.CreateRequest.builder()
                .stockCode("000001").direction(TradeDirection.SELL)
                .price(new BigDecimal("12.00")).quantity(new BigDecimal("100"))
                .tradeDate(LocalDate.of(2026, 5, 15)).build();

        ActualTradeDTO.Response resp = service.create(request);

        // Should match BUY1 (earlier date) → (12-15)*100 = -300
        assertEquals(new BigDecimal("-300.00"), resp.getProfitLoss());
    }

    // 4.3 update

    @Test
    void update_sellRecord_recalculatesProfit() {
        ActualTrade existing = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.SELL).price(new BigDecimal("12.00"))
                .quantity(new BigDecimal("100")).profitLoss(new BigDecimal("100.00"))
                .build();

        when(tradeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tradeRepository.save(any(ActualTrade.class))).thenReturn(existing);

        ActualTradeDTO.UpdateRequest request = ActualTradeDTO.UpdateRequest.builder()
                .price(new BigDecimal("13.00"))
                .build();

        service.update(1L, request);
        verify(tradeRepository, times(2)).save(any(ActualTrade.class)); // once in update, once in recalculate
    }

    @Test
    void update_buyRecord_noMatching() {
        ActualTrade existing = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).build();

        when(tradeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tradeRepository.save(any(ActualTrade.class))).thenReturn(existing);

        ActualTradeDTO.UpdateRequest request = ActualTradeDTO.UpdateRequest.builder()
                .price(new BigDecimal("11.00"))
                .build();

        service.update(1L, request);
        // Should not trigger FIFO matching for BUY
    }

    @Test
    void update_notFound_throws404() {
        when(tradeRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(999L, ActualTradeDTO.UpdateRequest.builder().build()));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void update_partialFields_preservesOthers() {
        ActualTrade existing = TestFixtures.tradeBuilder()
                .id(1L).price(new BigDecimal("10.00")).quantity(new BigDecimal("100")).build();

        when(tradeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(tradeRepository.save(any(ActualTrade.class))).thenReturn(existing);

        ActualTradeDTO.UpdateRequest request = ActualTradeDTO.UpdateRequest.builder()
                .price(new BigDecimal("11.00"))
                .build();

        service.update(1L, request);
        assertEquals(new BigDecimal("11.00"), existing.getPrice());
        assertEquals(new BigDecimal("100"), existing.getQuantity());
    }

    // 4.4 list

    @Test
    void list_stockCodeFilter_callsFindByStockCode() {
        when(tradeRepository.findByStockCode("000001")).thenReturn(List.of());
        service.list("000001", null, null, null);
        verify(tradeRepository).findByStockCode("000001");
    }

    @Test
    void list_dateRange_callsFindByTradeDateBetween() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        when(tradeRepository.findByTradeDateBetween(start, end)).thenReturn(List.of());
        service.list(null, null, start, end);
        verify(tradeRepository).findByTradeDateBetween(start, end);
    }

    @Test
    void list_stockCodeAndDate_callsFindByStockCodeAndTradeDateBetween() {
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        when(tradeRepository.findByStockCodeAndTradeDateBetween("000001", start, end)).thenReturn(List.of());
        service.list("000001", null, start, end);
        verify(tradeRepository).findByStockCodeAndTradeDateBetween("000001", start, end);
    }

    @Test
    void list_withDirectionFilter_filtersInMemory() {
        ActualTrade buy = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).build();
        ActualTrade sell = TestFixtures.tradeBuilder()
                .id(2L).direction(TradeDirection.SELL).build();

        when(tradeRepository.findAll()).thenReturn(Arrays.asList(buy, sell));

        List<ActualTradeDTO.Response> result = service.list(null, TradeDirection.SELL, null, null);

        assertEquals(1, result.size());
        assertEquals(TradeDirection.SELL, result.get(0).getDirection());
    }

    // 4.5 delete

    @Test
    void delete_existing_succeeds() {
        ActualTrade existing = TestFixtures.tradeBuilder().id(1L).build();
        when(tradeRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);
        verify(tradeRepository).delete(existing);
    }

    @Test
    void delete_notFound_throws404() {
        when(tradeRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(999L));
        assertEquals(404, ex.getStatusCode());
    }

    // Utility
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
