package com.exam.countdown;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.exam.countdown.mapper")
@SpringBootApplication
public class ExamCountdownApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamCountdownApplication.class, args);
    }
}
