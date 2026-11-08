package com.example.myapi.service;

import com.example.myapi.config.TushareConfig;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ShengyiMarketDataServiceTest {
    ObjectMapper mapper=new ObjectMapper();
    Map<String,JsonNode> row(String json)throws Exception {
        var node=mapper.readTree(json);Map<String,JsonNode> m=new HashMap<>();node.fields().forEachRemaining(e->m.put(e.getKey(),e.getValue()));return m;
    }
    ShengyiMarketDataService reader(){return spy(new ShengyiMarketDataService(new TushareConfig()));}
    @Test void mondayFindsFridayInUnorderedCalendar()throws Exception {
        var s=reader();doReturn(List.of(row("{\"cal_date\":\"20261109\",\"is_open\":1}"),row("{\"cal_date\":\"20261106\",\"is_open\":1}"),row("{\"cal_date\":\"20261108\",\"is_open\":0}"))).when(s).query(eq("trade_cal"),anyMap());
        var session=s.session(LocalDate.of(2026,11,9));assertTrue(session.open());assertEquals(LocalDate.of(2026,11,6),session.previous());
    }
    @Test void missingCalendarDoesNotAssumeOpen(){var s=reader();doReturn(List.of()).when(s).query(anyString(),anyMap());assertThrows(IllegalStateException.class,()->s.session(LocalDate.of(2026,11,9)));}
    @Test void staleLatestBarIsRejected()throws Exception {
        var s=reader();doReturn(List.of(row("{\"trade_date\":\"20240104\",\"close\":10,\"high\":11,\"low\":9,\"open\":10,\"vol\":100,\"pct_chg\":0}"))).when(s).query(eq("daily"),anyMap());
        doReturn(List.of(row("{\"trade_date\":\"20240104\",\"adj_factor\":2}"))).when(s).query(eq("adj_factor"),anyMap());
        assertThrows(IllegalStateException.class,()->s.load(LocalDate.of(2024,1,5)));
    }
    @Test void missingHistoricalFactorIsRejected()throws Exception {
        var s=reader();doReturn(List.of(row("{\"trade_date\":\"20240104\",\"close\":10,\"high\":11,\"low\":9,\"open\":10,\"vol\":100,\"pct_chg\":0}"),row("{\"trade_date\":\"20240105\",\"close\":10,\"high\":11,\"low\":9,\"open\":10,\"vol\":100,\"pct_chg\":0}"))).when(s).query(eq("daily"),anyMap());
        doReturn(List.of(row("{\"trade_date\":\"20240105\",\"adj_factor\":2}"))).when(s).query(eq("adj_factor"),anyMap());assertThrows(IllegalStateException.class,()->s.load(LocalDate.of(2024,1,5)));
    }
}
