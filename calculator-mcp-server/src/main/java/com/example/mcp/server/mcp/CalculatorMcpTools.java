package com.example.mcp.server.mcp;

import com.example.mcp.server.service.CalculatorService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP 工具定义。方法上的 @McpTool 会被 Spring AI 的注解扫描器自动注册到 MCP Server。
 */
@Component
public class CalculatorMcpTools {

    private final CalculatorService calculatorService;

    public CalculatorMcpTools(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @McpTool(name = "add", description = "计算两个数之和，返回 a + b")
    public double add(
            @McpToolParam(description = "第一个加数", required = true) double a,
            @McpToolParam(description = "第二个加数", required = true) double b) {
        return calculatorService.add(a, b);
    }

    @McpTool(name = "subtract", description = "计算两个数之差，返回 a - b")
    public double subtract(
            @McpToolParam(description = "被减数", required = true) double a,
            @McpToolParam(description = "减数", required = true) double b) {
        return calculatorService.subtract(a, b);
    }

    @McpTool(name = "multiply", description = "计算两个数之积，返回 a * b")
    public double multiply(
            @McpToolParam(description = "第一个乘数", required = true) double a,
            @McpToolParam(description = "第二个乘数", required = true) double b) {
        return calculatorService.multiply(a, b);
    }

    @McpTool(name = "divide", description = "计算两个数之商，返回 a / b；除数为 0 时返回错误")
    public double divide(
            @McpToolParam(description = "被除数", required = true) double a,
            @McpToolParam(description = "除数，不能为 0", required = true) double b) {
        return calculatorService.divide(a, b);
    }
}