package com.example.mcp.client.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI calculatorOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Calculator MCP Client API")
                .version("1.0.0")
                .description("MCP Client：通过 SSE 连接 calculator-mcp-server，用 DeepSeek 驱动工具调用"));
    }
}