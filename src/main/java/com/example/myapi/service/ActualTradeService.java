package com.example.myapi.service;

import com.example.myapi.dto.ActualTradeDTO;
import com.example.myapi.entity.ActualTrade;
import com.example.myapi.entity.TradeDirection;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.ActualTradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActualTradeService {

    private final ActualTradeRepository tradeRepository;

    @Transactional
    public ActualTradeDTO.Response create(ActualTradeDTO.CreateRequest request) {
        ActualTrade trade = ActualTrade.builder()
                .stockCode(request.getStockCode())
                .stockName(request.getStockName())
                .direction(request.getDirection())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .tradeDate(request.getTradeDate())
                .turnoverAmount(request.getTurnoverAmount())
                .settlementAmount(request.getSettlementAmount())
                .stampTax(defaultZero(request.getStampTax()))
                .transferFee(defaultZero(request.getTransferFee()))
                .commission(defaultZero(request.getCommission()))
                .otherFee(defaultZero(request.getOtherFee()))
                .totalFee(resolveTotalFee(request.getTotalFee(), request.getStampTax(),
                        request.getTransferFee(), request.getCommission(), request.getOtherFee()))
                .settlementAccountNumber(request.getSettlementAccountNumber())
                .settlementTradeType(request.getSettlementTradeType())
                .settlementUniqueKey(request.getSettlementUniqueKey())
                .settlementRecordId(request.getSettlementRecordId())
                .build();

        ActualTrade saved = tradeRepository.save(trade);

        if (request.getDirection() == TradeDirection.SELL) {
            matchAndCalculateProfitLoss(saved);
        }

        log.info("Created actual trade id={} stockCode={} direction={}",
                saved.getId(), saved.getStockCode(), saved.getDirection());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ActualTradeDTO.Response getById(Long id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<ActualTradeDTO.Response> list(String stockCode, TradeDirection direction,
                                               LocalDate startDate, LocalDate endDate) {
        List<ActualTrade> trades;
        if (stockCode != null && startDate != null && endDate != null) {
            trades = tradeRepository.findByStockCodeAndTradeDateBetween(stockCode, startDate, endDate);
        } else if (stockCode != null) {
            trades = tradeRepository.findByStockCode(stockCode);
        } else if (startDate != null && endDate != null) {
            trades = tradeRepository.findByTradeDateBetween(startDate, endDate);
        } else {
            trades = tradeRepository.findAll();
        }

        if (direction != null) {
            trades = trades.stream()
                    .filter(t -> t.getDirection() == direction)
                    .toList();
        }

        return trades.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ActualTradeDTO.Response update(Long id, ActualTradeDTO.UpdateRequest request) {
        ActualTrade trade = findById(id);
        if (request.getStockName() != null) trade.setStockName(request.getStockName());
        if (request.getDirection() != null) trade.setDirection(request.getDirection());
        if (request.getPrice() != null) trade.setPrice(request.getPrice());
        if (request.getQuantity() != null) trade.setQuantity(request.getQuantity());
        if (request.getTradeDate() != null) trade.setTradeDate(request.getTradeDate());
        if (request.getTurnoverAmount() != null) trade.setTurnoverAmount(request.getTurnoverAmount());
        if (request.getSettlementAmount() != null) trade.setSettlementAmount(request.getSettlementAmount());
        if (request.getStampTax() != null) trade.setStampTax(request.getStampTax());
        if (request.getTransferFee() != null) trade.setTransferFee(request.getTransferFee());
        if (request.getCommission() != null) trade.setCommission(request.getCommission());
        if (request.getOtherFee() != null) trade.setOtherFee(request.getOtherFee());
        if (request.getTotalFee() != null) trade.setTotalFee(request.getTotalFee());
        if (request.getSettlementAccountNumber() != null) trade.setSettlementAccountNumber(request.getSettlementAccountNumber());
        if (request.getSettlementTradeType() != null) trade.setSettlementTradeType(request.getSettlementTradeType());
        if (request.getSettlementUniqueKey() != null) trade.setSettlementUniqueKey(request.getSettlementUniqueKey());
        if (request.getSettlementRecordId() != null) trade.setSettlementRecordId(request.getSettlementRecordId());

        ActualTrade saved = tradeRepository.save(trade);

        if (saved.getDirection() == TradeDirection.SELL) {
            recalculateSellProfit(saved);
        }

        log.info("Updated actual trade id={}", id);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        ActualTrade trade = findById(id);
        tradeRepository.delete(trade);
        log.info("Deleted actual trade id={}", id);
    }

    public ActualTrade findById(Long id) {
        return tradeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("实盘记录不存在: " + id, 404));
    }

    public List<ActualTrade> findUnmatchedBuys(String stockCode) {
        return tradeRepository.findUnmatchedBuys(stockCode);
    }

    private void matchAndCalculateProfitLoss(ActualTrade sellTrade) {
        List<ActualTrade> buys = tradeRepository.findUnmatchedBuys(sellTrade.getStockCode()).stream()
                .sorted(Comparator.comparing(ActualTrade::getTradeDate))
                .toList();

        if (buys.isEmpty()) {
            log.warn("No unmatched buys found for stockCode={}, sell trade id={}",
                    sellTrade.getStockCode(), sellTrade.getId());
            return;
        }

        BigDecimal remainingQty = sellTrade.getQuantity();
        BigDecimal totalCost = BigDecimal.ZERO;
        long matchedBuyCount = 0;

        for (ActualTrade buy : buys) {
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal matchQty = remainingQty.min(buy.getQuantity());
            totalCost = totalCost.add(getBuyCostForQuantity(buy, matchQty));
            remainingQty = remainingQty.subtract(matchQty);
            matchedBuyCount++;

            buy.setIsMatched(true);
            buy.setMatchedBuyId(buy.getId());
            tradeRepository.save(buy);
        }

        if (matchedBuyCount > 0) {
            BigDecimal sellProceeds = getSellProceeds(sellTrade);
            BigDecimal profitLoss = sellProceeds.subtract(totalCost)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal profitLossPercent = profitLoss
                    .divide(totalCost, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);

            sellTrade.setProfitLoss(profitLoss);
            sellTrade.setProfitLossPercent(profitLossPercent);
            sellTrade.setIsMatched(true);
            sellTrade.setMatchedBuyId(buys.get(0).getId());
            tradeRepository.save(sellTrade);
        }
    }

    private void recalculateSellProfit(ActualTrade sellTrade) {
        sellTrade.setProfitLoss(null);
        sellTrade.setProfitLossPercent(null);
        sellTrade.setIsMatched(false);
        sellTrade.setMatchedBuyId(null);
        matchAndCalculateProfitLoss(tradeRepository.save(sellTrade));
    }

    private BigDecimal getBuyCostForQuantity(ActualTrade buy, BigDecimal matchQty) {
        BigDecimal fullCost = buy.getSettlementAmount() != null
                ? buy.getSettlementAmount().abs()
                : buy.getPrice().multiply(buy.getQuantity()).add(defaultZero(buy.getTotalFee()));
        return fullCost
                .multiply(matchQty)
                .divide(buy.getQuantity(), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal getSellProceeds(ActualTrade sellTrade) {
        if (sellTrade.getSettlementAmount() != null) {
            return sellTrade.getSettlementAmount().abs();
        }
        return sellTrade.getPrice()
                .multiply(sellTrade.getQuantity())
                .subtract(defaultZero(sellTrade.getTotalFee()));
    }

    private BigDecimal resolveTotalFee(BigDecimal totalFee, BigDecimal stampTax, BigDecimal transferFee,
                                       BigDecimal commission, BigDecimal otherFee) {
        if (totalFee != null) {
            return totalFee;
        }
        return defaultZero(stampTax)
                .add(defaultZero(transferFee))
                .add(defaultZero(commission))
                .add(defaultZero(otherFee));
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private ActualTradeDTO.Response toResponse(ActualTrade trade) {
        return ActualTradeDTO.Response.builder()
                .id(trade.getId())
                .stockCode(trade.getStockCode())
                .stockName(trade.getStockName())
                .direction(trade.getDirection())
                .price(trade.getPrice())
                .quantity(trade.getQuantity())
                .tradeDate(trade.getTradeDate())
                .turnoverAmount(trade.getTurnoverAmount())
                .settlementAmount(trade.getSettlementAmount())
                .stampTax(trade.getStampTax())
                .transferFee(trade.getTransferFee())
                .commission(trade.getCommission())
                .otherFee(trade.getOtherFee())
                .totalFee(trade.getTotalFee())
                .settlementAccountNumber(trade.getSettlementAccountNumber())
                .settlementTradeType(trade.getSettlementTradeType())
                .settlementUniqueKey(trade.getSettlementUniqueKey())
                .settlementRecordId(trade.getSettlementRecordId())
                .profitLossAmount(trade.getProfitLoss())
                .profitLossPercent(trade.getProfitLossPercent())
                .matched(trade.getIsMatched())
                .matchedBuyId(trade.getMatchedBuyId())
                .createdAt(trade.getCreatedAt())
                .updatedAt(trade.getUpdatedAt())
                .build();
    }
}
