package com.xiangjiu.mybatisrel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MybatisRelApplication {

    public static void main(String[] args) {
        SpringApplication.run(MybatisRelApplication.class, args);
    }
}
