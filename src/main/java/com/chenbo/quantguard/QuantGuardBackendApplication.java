package com.chenbo.quantguard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.chenbo.quantguard.mapper")
public class QuantGuardBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantGuardBackendApplication.class, args);
    }

}
