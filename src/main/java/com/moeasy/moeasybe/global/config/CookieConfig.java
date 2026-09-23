package com.moeasy.moeasybe.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CookieProperties.class)
public class CookieConfig {
}
