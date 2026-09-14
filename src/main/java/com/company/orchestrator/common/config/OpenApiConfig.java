package com.company.orchestrator.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 文档（/v3/api-docs，Swagger UI：/swagger-ui.html）。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("AI Resource Orchestrator API")
                .description("AI 项目能力规划与人力资源编排平台 API / "
                        + "AI-powered project capability planning & workforce orchestration API")
                .version("v0.1.0")
                .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")));
    }
}
