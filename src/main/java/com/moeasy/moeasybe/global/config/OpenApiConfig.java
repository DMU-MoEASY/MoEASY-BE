package com.moeasy.moeasybe.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Configures the OpenAPI definition and bearer authentication scheme. */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MoEASY API",
                version = "v1",
                description = "MoEASY 백엔드 API 문서"
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        in = SecuritySchemeIn.HEADER,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {

    /** Creates the OpenAPI configuration. */
    public OpenApiConfig() {
    }
}
