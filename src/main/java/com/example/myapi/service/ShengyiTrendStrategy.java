package com.example.myapi.service;

import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;

/** Frozen trend/volume rule, matching the 2026-11-08 research implementation. */
@Service
public class ShengyiTrendStrategy {
    public static final String VERSION = "shengyi-ma10-vol1.2-v1";
    public record Bar(LocalDate date, double rawClose, double close, double high,
                      double low, double volume, double pctChange) {}
    public record Review(LocalDate signalDate, String signal, double closePrice,
                         double pctChange, double volumeWanLots, double adjustedClose,
                         double ma10, double k, double d, double j, double previousJ,
                         double histogram, double previousHistogram, double dif,
                         double dea, double volumeRatio, String reason) {
        public String action() {
            return switch (signal) {
                case "BUY" -> "有现金且未满仓：拟买入总权益1/3，以可用现金为限";
                case "SELL" -> "有持仓：拟卖出总权益1/3，以可用持股为限";
                default -> "继续持仓；空仓则继续观望";
            };
        }
    }

    public Review evaluate(List<Bar> bars) {
        int n = bars.size();
        if (n < 100) throw new IllegalArgumentException("指标预热数据不足");
        double[] ma=new double[n], hist=new double[n], js=new double[n];
        double e12=bars.get(0).close(), e26=e12, dea=0, k=Double.NaN, d=Double.NaN, dif=0;
        for (int i=0;i<n;i++) {
            Bar b=bars.get(i);
            if (i>0 && !b.date().isAfter(bars.get(i-1).date())) throw new IllegalArgumentException("行情日期必须唯一递增");
            if (!Double.isFinite(b.close()+b.high()+b.low()+b.volume()+b.rawClose()+b.pctChange()) ||
                b.close()<=0 || b.rawClose()<=0 || b.high()<b.close() || b.low()>b.close() || b.low()<=0 || b.volume()<0)
                throw new IllegalArgumentException("行情数据无效");
            if (i>0) { e12+=(b.close()-e12)*2/13; e26+=(b.close()-e26)*2/27; }
            dif=e12-e26; dea=dea*.8+dif*.2; hist[i]=2*(dif-dea);
            if (i>=8) {
                double high=Double.NEGATIVE_INFINITY, low=Double.POSITIVE_INFINITY;
                for (int q=i-8;q<=i;q++) { high=Math.max(high,bars.get(q).high());low=Math.min(low,bars.get(q).low()); }
                if (high<=low) throw new IllegalArgumentException("九日价格区间无效");
                double rsv=100*(b.close()-low)/(high-low);
                if (Double.isNaN(k)) { k=rsv;d=rsv; } else { k=(2*k+rsv)/3;d=(2*d+k)/3; }
                js[i]=3*k-2*d;
            }
            if(i>=9) { double sum=0;for(int q=i-9;q<=i;q++)sum+=bars.get(q).close();ma[i]=sum/10; }
        }
        Bar last=bars.get(n-1);double vs=0;for(int q=n-21;q<n-1;q++)vs+=bars.get(q).volume();
        if(vs<=0 || last.volume()<=0)throw new IllegalArgumentException("缺少有效交易量");
        double vr=last.volume()/(vs/20);
        boolean above=last.close()>ma[n-1], risingJ=js[n-1]>js[n-2], risingH=hist[n-1]>hist[n-2];
        boolean below=last.close()<ma[n-1], fallingH=hist[n-1]<hist[n-2];
        boolean volume=vr>=1.2, persistent=bars.get(n-2).close()<ma[n-2] && bars.get(n-3).close()<ma[n-3];
        String signal=above && risingJ && risingH && volume ? "BUY" : below && fallingH && (volume || persistent) ? "SELL" : "HOLD";
        String reason=String.format(Locale.ROOT,"收盘%sMA10；J%s；MACD柱%s；量比%.2f%s1.2；此前两日连续低于均线：%s",
                above?">":below?"<":"=",risingJ?"上升":js[n-1]<js[n-2]?"下降":"持平",
                risingH?"上升":fallingH?"下降":"持平",vr,volume?">=":"<",persistent?"是":"否");
        return new Review(last.date(),signal,last.rawClose(),last.pctChange(),last.volume()/10000,last.close(),
                ma[n-1],k,d,js[n-1],js[n-2],hist[n-1],hist[n-2],dif,dea,vr,reason);
    }
}
