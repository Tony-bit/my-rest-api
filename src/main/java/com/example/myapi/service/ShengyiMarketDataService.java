package com.example.myapi.service;

import com.example.myapi.config.TushareConfig;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Strict calendar and adjusted market-data reader; independent of legacy Xueqiu logic. */
@Service
@RequiredArgsConstructor
public class ShengyiMarketDataService {
    private final TushareConfig config;
    private final HttpClient client=HttpClient.newHttpClient();
    private final ObjectMapper mapper=new ObjectMapper();
    private static final DateTimeFormatter F=DateTimeFormatter.BASIC_ISO_DATE;
    public record Session(boolean open, LocalDate previous) {}

    public Session session(LocalDate executionDate) {
        List<Map<String,JsonNode>> rows=query("trade_cal",Map.of("exchange","SSE","start_date",F.format(executionDate.minusDays(60)),"end_date",F.format(executionDate)));
        Map<String,JsonNode> today=null;LocalDate previous=null;
        for(var r:rows) {
            LocalDate day=LocalDate.parse(text(r,"cal_date"),F);int open=Integer.parseInt(text(r,"is_open"));
            if (day.equals(executionDate)) { if(today!=null)throw new IllegalStateException("交易日历日期重复");today=r; }
            if (day.isBefore(executionDate) && open==1 && (previous==null || day.isAfter(previous))) previous=day;
        }
        if(today==null)throw new IllegalStateException("交易日历缺少执行日");
        int open=Integer.parseInt(text(today,"is_open"));
        if(open!=0 && open!=1)throw new IllegalStateException("交易日历开市标志无效");
        if(open==1 && previous==null)throw new IllegalStateException("交易日历缺少上一交易日");
        return new Session(open==1,previous);
    }

    public List<ShengyiTrendStrategy.Bar> load(LocalDate expected) {
        SortedMap<String,Map<String,JsonNode>> prices=new TreeMap<>();Map<String,Double> factors=new HashMap<>();
        for(int year=2024;year<=expected.getYear();year++) {
            String start=year+"0101",end=year==expected.getYear()?F.format(expected):year+"1231";
            var params=Map.<String,Object>of("ts_code","600183.SH","start_date",start,"end_date",end);
            var daily=query("daily",params);var adj=query("adj_factor",params);
            if(daily.isEmpty() || adj.isEmpty())throw new IllegalStateException("日线或复权分段数据为空");
            for(var r:daily) {String date=text(r,"trade_date");if(prices.putIfAbsent(date,r)!=null)throw new IllegalStateException("日线日期重复");}
            for(var r:adj) {String date=text(r,"trade_date");if(factors.putIfAbsent(date,number(r,"adj_factor"))!=null)throw new IllegalStateException("复权日期重复");}
        }
        if(prices.isEmpty() || !prices.lastKey().equals(F.format(expected)))throw new IllegalStateException("上一交易日行情未更新或停牌");
        double anchor=Optional.ofNullable(factors.get(prices.lastKey())).orElseThrow(()->new IllegalStateException("最新复权因子缺失"));
        if(anchor<=0)throw new IllegalStateException("复权因子无效");
        List<ShengyiTrendStrategy.Bar> result=new ArrayList<>();
        for(var e:prices.entrySet()) {
            var r=e.getValue();Double factor=factors.get(e.getKey());
            if(factor==null || !Double.isFinite(factor) || factor<=0)throw new IllegalStateException("历史复权因子缺失或无效");
            double close=number(r,"close"),high=number(r,"high"),low=number(r,"low"),open=number(r,"open");
            if(high<Math.max(open,close) || low>Math.min(open,close))throw new IllegalStateException("OHLC关系无效");
            result.add(new ShengyiTrendStrategy.Bar(LocalDate.parse(e.getKey(),F),close,close*factor/anchor,
                    high*factor/anchor,low*factor/anchor,number(r,"vol"),number(r,"pct_chg")));
        }
        return result;
    }

    public List<Map<String,JsonNode>> query(String api,Map<String,Object> params) {
        if(config.getToken()==null || config.getToken().isBlank())throw new IllegalStateException("Tushare未配置");
        try {
            var body=Map.of("api_name",api,"token",config.getToken(),"params",params,"fields","");
            var request=HttpRequest.newBuilder(URI.create(config.getBaseUrl())).timeout(Duration.ofMillis(config.getTimeout()))
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException("Tushare HTTP状态异常");
            var root=mapper.readTree(response.body());
            if(!root.has("code") || root.get("code").asInt()!=0)throw new IllegalStateException("Tushare接口返回错误");
            var data=root.path("data");var fields=data.path("fields");var items=data.path("items");
            if(!fields.isArray() || !items.isArray())throw new IllegalStateException("Tushare响应结构不完整");
            List<Map<String,JsonNode>> result=new ArrayList<>();
            for(var row:items) {
                if(!row.isArray() || row.size()!=fields.size())throw new IllegalStateException("Tushare字段数量不匹配");
                Map<String,JsonNode> parsed=new HashMap<>();
                for(int i=0;i<fields.size();i++)parsed.put(fields.get(i).asText(),row.get(i));
                result.add(parsed);
            }
            return result;
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Tushare请求中断");}
        catch(Exception e){throw new IllegalStateException("Tushare请求失败："+api);}
    }
    private static String text(Map<String,JsonNode> r,String name){var n=r.get(name);if(n==null || n.isNull())throw new IllegalStateException("缺少字段："+name);return n.asText();}
    private static double number(Map<String,JsonNode> r,String name){double v=Double.parseDouble(text(r,name));if(!Double.isFinite(v))throw new IllegalStateException("无效数值："+name);return v;}
}
