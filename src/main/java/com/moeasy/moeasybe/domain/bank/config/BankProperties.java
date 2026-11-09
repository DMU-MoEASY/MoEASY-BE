package com.moeasy.moeasybe.domain.bank.config;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bank-api")
@Getter
@Setter
public class BankProperties {
    private String rateLimitPrefix = "bank:requests";
    private String encryptionKey = "";
    private String baseUrl = "https://api.bankapi.co.kr";
}
