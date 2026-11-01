package com.ceos24.cgv.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "toss")
public record TossProperties(String secretKey, String baseUrl) {}
