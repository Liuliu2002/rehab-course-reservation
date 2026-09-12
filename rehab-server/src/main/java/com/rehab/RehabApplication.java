package com.rehab;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@MapperScan("com.rehab.mapper")
public class RehabApplication {
    public static void main(String[] args) {
        SpringApplication.run(RehabApplication.class, args);
    }
}
