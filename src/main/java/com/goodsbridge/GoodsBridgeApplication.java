package com.goodsbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GoodsBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodsBridgeApplication.class, args);
    }

}
