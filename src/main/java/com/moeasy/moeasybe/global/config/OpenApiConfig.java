package com.moeasy.moeasybe.global.config;

import com.moeasy.moeasybe.global.security.SecurityRequestMatchers;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
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
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "액세스 JWT 원문을 입력하세요. 상태 변경 요청에는 별도의 CSRF 쿠키와 X-XSRF-TOKEN 헤더가 필요합니다."
)
public class OpenApiConfig {

    /** OpenAPI 설정을 생성합니다. */
    public OpenApiConfig() {
    }

    @Bean
    OpenApiCustomizer bearerAuthenticationCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperations().forEach(operation -> operation.setSecurity(
                        SecurityRequestMatchers.isPublicPath(path)
                                ? List.of()
                                : List.of(new SecurityRequirement().addList("bearerAuth")))));
    }
}
