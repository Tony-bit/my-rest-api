package com.example.myapi.scheduler;

import com.example.myapi.service.ShengyiReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="shengyi.review.enabled",havingValue="true")
public class ShengyiReviewTask {
    private final ShengyiReviewService review;
    @Scheduled(cron="${shengyi.review.cron:0 0 9 * * MON-FRI}",zone="Asia/Shanghai")
    public void run(){
        try{log.info("Shengyi daily review result={}",review.deliver(LocalDate.now(ZoneId.of("Asia/Shanghai"))));}
        catch(Exception e){log.error("Shengyi daily review failed; check market data, Tushare and WeChat configuration");}
    }
}
