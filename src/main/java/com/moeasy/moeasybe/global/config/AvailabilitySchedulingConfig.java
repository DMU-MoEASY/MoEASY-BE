package com.moeasy.moeasybe.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AvailabilitySchedulingConfig {

    @Bean
    public ThreadPoolTaskScheduler availabilityDeadlineScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("availability-deadline-");
        return scheduler;
    }

    @Bean
    public ThreadPoolTaskScheduler availabilitySocketScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("availability-socket-");
        return scheduler;
    }
}
