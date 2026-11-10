package com.moeasy.moeasybe.domain.group.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GroupTierPolicyTest {
    @ParameterizedTest
    @CsvSource({
            "0,0,0,0,5,bronze", "4,4,10,20,5,silver", "4,4,20,20,5,silver",
            "8,1,5,5,5,bronze", "8,4,10,20,5,silver", "8,8,16,40,5,silver",
            "8,8,20,40,5,gold", "8,8,40,40,4,bronze", "8,8,49999999999,100000000000,5,silver"
    })
    void tierUsesExactThresholds(long activities, long evaluated, long numerator, long denominator, long members, String expected) {
        assertThat(GroupTierPolicy.calculate(activities, evaluated, numerator, denominator, members).tier()).isEqualTo(expected);
    }
}
