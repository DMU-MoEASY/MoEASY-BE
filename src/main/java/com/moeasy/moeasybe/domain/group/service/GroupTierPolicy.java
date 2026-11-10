package com.moeasy.moeasybe.domain.group.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record GroupTierPolicy(BigDecimal score, String tier) {
    public static GroupTierPolicy calculate(long activities, long evaluated, long numerator, long denominator, long members) {
        BigDecimal divisor = BigDecimal.valueOf(Math.max(denominator, 1));
        BigDecimal weighted = BigDecimal.valueOf(Math.min(activities, 8)).multiply(new BigDecimal("7.5"))
                .multiply(divisor).add(BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(40)));
        String tier = "bronze";
        if (members >= 5 && evaluated >= 8 && weighted.compareTo(divisor.multiply(BigDecimal.valueOf(80))) >= 0) {
            tier = "gold";
        } else if (members >= 5 && evaluated >= 4 && weighted.compareTo(divisor.multiply(BigDecimal.valueOf(50))) >= 0) {
            tier = "silver";
        }
        return new GroupTierPolicy(weighted.divide(divisor, 8, RoundingMode.HALF_UP), tier);
    }
}
