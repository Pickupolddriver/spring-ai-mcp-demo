package com.example.mcp.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI calculatorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Calculator MCP Server API")
                .version("1.0.0")
                .description("四则运算 REST 接口；同一个 CalculatorService 也通过 POST /mcp 暴露为 MCP 工具"));
    }
}