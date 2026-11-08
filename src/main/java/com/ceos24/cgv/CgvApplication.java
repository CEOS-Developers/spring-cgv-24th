package com.ceos24.cgv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class CgvApplication {

    public static void main(String[] args) {
        SpringApplication.run(CgvApplication.class, args);
    }
}
