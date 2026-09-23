package com.moeasy.moeasybe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class MoEasyBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoEasyBeApplication.class, args);
    }

}
