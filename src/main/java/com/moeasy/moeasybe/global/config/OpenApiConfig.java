package com.moeasy.moeasybe.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 문서의 기본 정보를 구성합니다. */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MoEASY API",
                version = "v1",
                description = "MoEASY 백엔드 API 문서"
        )
)
public class OpenApiConfig {

    /** OpenAPI 설정을 생성합니다. */
    public OpenApiConfig() {
    }
}
