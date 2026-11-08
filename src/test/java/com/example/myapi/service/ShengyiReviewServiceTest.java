package com.example.myapi.service;

import com.example.myapi.entity.ShengyiReviewDelivery;
import com.example.myapi.repository.ShengyiReviewDeliveryRepository;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ShengyiReviewServiceTest {
    ShengyiMarketDataService market=mock(ShengyiMarketDataService.class);
    ShengyiTrendStrategy strategy=mock(ShengyiTrendStrategy.class);
    WeChatNotificationService sender=mock(WeChatNotificationService.class);
    ShengyiReviewDeliveryRepository repo=mock(ShengyiReviewDeliveryRepository.class);
    ShengyiReviewService service=new ShengyiReviewService(market,strategy,sender,repo);
    LocalDate monday=LocalDate.of(2026,11,9),friday=LocalDate.of(2026,11,6);
    ShengyiTrendStrategy.Review hold=new ShengyiTrendStrategy.Review(friday,"HOLD",70.49,-9.37,98.35,70.49,86.65,9,10,7.8,18.8,-5.37,-5.32,-14.8,-12.1,1.47,"测试依据");
    void ready(){when(market.session(monday)).thenReturn(new ShengyiMarketDataService.Session(true,friday));when(market.load(friday)).thenReturn(List.of());when(strategy.evaluate(anyList())).thenReturn(hold);when(repo.saveAndFlush(any())).thenAnswer(a->a.getArgument(0));}
    @Test void closedDayDoesNotFetchHistoryOrSend(){when(market.session(monday)).thenReturn(new ShengyiMarketDataService.Session(false,friday));assertEquals("MARKET_CLOSED",service.deliver(monday));verify(market,never()).load(any());verifyNoInteractions(sender);}
    @Test void previewHasNoDeliverySideEffects(){ready();assertEquals(friday,service.preview(monday).review().signalDate());verifyNoInteractions(sender,repo);}
    @Test void sendsHoldAndRecordsSuccess(){ready();assertEquals("SENT",service.deliver(monday));verify(sender).sendShengyiReview(monday,hold);verify(repo,times(2)).saveAndFlush(argThat(x->"SENT".equals(x.getStatus())));}
    @Test void alreadyAttemptedDoesNotResend(){when(repo.existsByStockCodeAndExecutionDateAndRuleVersion(anyString(),eq(monday),anyString())).thenReturn(true);assertEquals("ALREADY_ATTEMPTED",service.deliver(monday));verifyNoInteractions(market,sender);}
    @Test void concurrentClaimLoserDoesNotSend(){ready();when(repo.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique constraint"));assertEquals("ALREADY_ATTEMPTED",service.deliver(monday));verifyNoInteractions(sender);}
    @Test void failureNeverBecomesSuccess(){ready();doThrow(new IllegalStateException("network uncertainty")).when(sender).sendShengyiReview(any(),any());assertThrows(IllegalStateException.class,()->service.deliver(monday));verify(repo,times(2)).saveAndFlush(argThat(x->"FAILED".equals(x.getStatus())));}
    @Test void missingDataNeverClaimsOrNotifies(){when(market.session(monday)).thenReturn(new ShengyiMarketDataService.Session(true,friday));when(market.load(friday)).thenThrow(new IllegalStateException("stale"));assertThrows(IllegalStateException.class,()->service.deliver(monday));verify(repo,never()).saveAndFlush(any());verifyNoInteractions(sender);}
}
