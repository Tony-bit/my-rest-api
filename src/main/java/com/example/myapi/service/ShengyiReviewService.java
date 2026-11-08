package com.example.myapi.service;

import com.example.myapi.entity.ShengyiReviewDelivery;
import com.example.myapi.repository.ShengyiReviewDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import java.time.*;

@Service @RequiredArgsConstructor
public class ShengyiReviewService {
    private final ShengyiMarketDataService market;
    private final ShengyiTrendStrategy strategy;
    private final WeChatNotificationService notifications;
    private final ShengyiReviewDeliveryRepository deliveries;
    public record Preview(String status,LocalDate executionDate,String ruleVersion,ShengyiTrendStrategy.Review review) {}
    public Preview preview(LocalDate executionDate) {
        var session=market.session(executionDate);
        if(!session.open())return new Preview("MARKET_CLOSED",executionDate,ShengyiTrendStrategy.VERSION,null);
        var review=strategy.evaluate(market.load(session.previous()));
        if(!review.signalDate().equals(session.previous()))throw new IllegalStateException("行情日期与上一交易日不匹配");
        return new Preview("OK",executionDate,ShengyiTrendStrategy.VERSION,review);
    }
    /** A durable claim precedes HTTP delivery. Failed/uncertain attempts are not blindly retried. */
    public synchronized String deliver(LocalDate executionDate) {
        if(deliveries.existsByStockCodeAndExecutionDateAndRuleVersion("600183.SH",executionDate,ShengyiTrendStrategy.VERSION))return "ALREADY_ATTEMPTED";
        var p=preview(executionDate);
        if(!"OK".equals(p.status()))return p.status();
        var record=new ShengyiReviewDelivery();record.setExecutionDate(executionDate);record.setRuleVersion(ShengyiTrendStrategy.VERSION);
        record.setSignalDate(p.review().signalDate());record.setSignal(p.review().signal());
        try{record=deliveries.saveAndFlush(record);}catch(DataIntegrityViolationException e){return "ALREADY_ATTEMPTED";}
        try{
            notifications.sendShengyiReview(executionDate,p.review());
            record.setStatus("SENT");record.setSentAt(Instant.now());deliveries.saveAndFlush(record);
            return "SENT";
        }catch(Exception e){
            record.setStatus("FAILED");record.setFailureReason("投递失败或结果不确定，请核对微信和服务日志，勿盲目重发");
            deliveries.saveAndFlush(record);throw new IllegalStateException("生益科技复盘投递失败或结果不确定");
        }
    }
}
