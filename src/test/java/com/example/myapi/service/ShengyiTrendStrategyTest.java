package com.example.myapi.service;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShengyiTrendStrategyTest {
    @Test void matchesEveryResearchSignalAndLatestIndicator() throws Exception {
        var strategy=new ShengyiTrendStrategy();var bars=new ArrayList<ShengyiTrendStrategy.Bar>();int checked=0;String[] last=null;
        try(var stream=getClass().getResourceAsStream("/shengyi/research-fixture.csv")) {
            assertNotNull(stream);
            var lines=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).lines().skip(1).toList();
            for(String line:lines){
                var a=line.split(",",-1);last=a;
                bars.add(new ShengyiTrendStrategy.Bar(LocalDate.parse(a[0],DateTimeFormatter.BASIC_ISO_DATE),
                        Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3]),Double.parseDouble(a[4]),Double.parseDouble(a[5]),Double.parseDouble(a[6])));
                if(a[0].compareTo("20260508")>=0){assertEquals(a[7],strategy.evaluate(bars).signal(),a[0]);checked++;}
            }
        }
        assertEquals(124,checked);var r=strategy.evaluate(bars);
        assertEquals(Double.parseDouble(last[8]),r.j(),1e-8);
        assertEquals(Double.parseDouble(last[9]),r.histogram(),1e-8);
        assertEquals(Double.parseDouble(last[10]),r.ma10(),1e-8);
        assertEquals(Double.parseDouble(last[11]),r.volumeRatio(),1e-8);
        assertEquals("SELL",r.signal());assertTrue(r.action().contains("有持仓"));
        var reversed=new ArrayList<>(bars);Collections.reverse(reversed);
        assertThrows(IllegalArgumentException.class,()->strategy.evaluate(reversed));
        assertThrows(IllegalArgumentException.class,()->strategy.evaluate(bars.subList(0,50)));
    }
}
