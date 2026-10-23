package com.fincore.nexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FinCoreNexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinCoreNexusApplication.class, args);
    }
}
