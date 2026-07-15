package com.glassvue.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui.html, OpenAPI 문서: /v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI espOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ESP API")
                        .description("사내 게시판 연습 프로젝트 API 문서")
                        .version("v0.0.1"));
    }
}
