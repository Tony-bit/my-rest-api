package com.example.myapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MyRestApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyRestApiApplication.class, args);
    }
}
