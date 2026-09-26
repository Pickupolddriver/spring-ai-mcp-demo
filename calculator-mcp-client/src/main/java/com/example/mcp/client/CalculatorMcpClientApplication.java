package com.example.mcp.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Client 启动类：通过 SSE 连接 calculator-mcp-server，
 * 并把 DeepSeek 模型与远端 MCP 工具组合成一个「会算数的聊天助手」。
 */
@SpringBootApplication
public class CalculatorMcpClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(CalculatorMcpClientApplication.class, args);
    }
}