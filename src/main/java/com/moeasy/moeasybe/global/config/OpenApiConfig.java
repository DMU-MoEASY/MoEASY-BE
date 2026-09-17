package com.moeasy.moeasybe.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 정의와 Bearer 인증 방식을 구성합니다. */
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

    /** OpenAPI 설정을 생성합니다. */
    public OpenApiConfig() {
    }
}
