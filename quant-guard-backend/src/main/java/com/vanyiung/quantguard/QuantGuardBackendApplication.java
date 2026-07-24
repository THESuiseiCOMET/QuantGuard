package com.vanyiung.quantguard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.vanyiung.quantguard.mapper")
public class QuantGuardBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuantGuardBackendApplication.class, args);
    }

}
