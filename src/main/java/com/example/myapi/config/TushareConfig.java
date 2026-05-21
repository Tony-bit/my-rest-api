package com.example.myapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "tushare")
public class TushareConfig {
    private String token;
    private String baseUrl = "https://api.tushare.pro";
    private int timeout = 30000;
}
