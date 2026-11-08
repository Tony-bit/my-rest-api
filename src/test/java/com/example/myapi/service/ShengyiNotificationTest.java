package com.example.myapi.service;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ShengyiNotificationTest {
    @ParameterizedTest
    @CsvSource({"BUY,买入总权益1/3","SELL,卖出总权益1/3","HOLD,继续持仓或观望"})
    void usesCorrectActionAndIncludesEvidence(String signal,String expected){
        var r=new ShengyiTrendStrategy.Review(LocalDate.of(2026,11,6),signal,70.49,-9.37,98.35,70.49,86.65,9,10,7.8,18.8,-5.37,-5.32,-14.8,-12.1,1.47,"依据");
        var data=WeChatNotificationService.shengyiTemplateData(LocalDate.of(2026,11,9),r);
        assertEquals(expected,((Map<?,?>)data.get("keyword2")).get("value"));
        String detail=(String)((Map<?,?>)data.get("remark")).get("value");
        assertTrue(detail.contains("70.49"));assertTrue(detail.contains("1.47"));
        assertTrue(detail.contains("18.80→7.80"));assertTrue(detail.contains("-5.3200→-5.3700"));
        assertTrue(((Map<?,?>)data.get("keyword3")).get("value").toString().contains("2026-11-06"));
        if(signal.equals("HOLD"))assertFalse(((Map<?,?>)data.get("first")).get("value").toString().contains("卖出"));
    }
}
