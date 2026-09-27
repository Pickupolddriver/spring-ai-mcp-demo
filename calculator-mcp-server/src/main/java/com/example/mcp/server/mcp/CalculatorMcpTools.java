package com.example.mcp.server.mcp;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.example.mcp.server.service.CalculatorService;

/**
 * MCP 工具定义。方法上的 @McpTool 会被 Spring AI 的注解扫描器自动注册到 MCP Server。
 * <p>四个运算都是纯函数：不改任何状态（readOnlyHint）、可重复调用结果一致（idempotentHint）、
 * 不触碰外部世界（openWorldHint=false）、无破坏性（destructiveHint=false）。
 * 这四个提示会随 tools/list 一起下发给客户端，客户端据此判断是否需要额外弹窗确认。</p>
 */
@Component
public class CalculatorMcpTools {

    private final CalculatorService calculatorService;

    public CalculatorMcpTools(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @McpTool(name = "add", title = "加法", description = "计算两个数之和，返回 a + b",
            annotations = @McpTool.McpAnnotations(title = "加法", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public double add(
            @McpToolParam(description = "第一个加数", required = true) double a,
            @McpToolParam(description = "第二个加数", required = true) double b) {
        return calculatorService.add(a, b);
    }

    @McpTool(name = "subtract", title = "减法", description = "计算两个数之差，返回 a - b",
            annotations = @McpTool.McpAnnotations(title = "减法", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public double subtract(
            @McpToolParam(description = "被减数", required = true) double a,
            @McpToolParam(description = "减数", required = true) double b) {
        return calculatorService.subtract(a, b);
    }

    @McpTool(name = "multiply", title = "乘法", description = "计算两个数之积，返回 a * b",
            annotations = @McpTool.McpAnnotations(title = "乘法", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public double multiply(
            @McpToolParam(description = "第一个乘数", required = true) double a,
            @McpToolParam(description = "第二个乘数", required = true) double b) {
        return calculatorService.multiply(a, b);
    }

    @McpTool(name = "divide", title = "除法", description = "计算两个数之商，返回 a / b；除数为 0 时返回错误",
            annotations = @McpTool.McpAnnotations(title = "除法", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public double divide(
            @McpToolParam(description = "被除数", required = true) double a,
            @McpToolParam(description = "除数，不能为 0", required = true) double b) {
        return calculatorService.divide(a, b);
    }
}