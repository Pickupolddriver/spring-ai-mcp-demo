package com.example.mcp.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Server 启动类：同时提供四则运算的 MCP 工具（Streamable HTTP）与 REST API。
 */
@SpringBootApplication
public class CalculatorMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalculatorMcpServerApplication.class, args);
    }
}