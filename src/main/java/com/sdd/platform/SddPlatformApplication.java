package com.sdd.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.sdd.platform.config")
@MapperScan("com.sdd.platform.infrastructure.persistence")
public class SddPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(SddPlatformApplication.class, args);
    }
}
