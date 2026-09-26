package com.example.mcp.server.api;

import com.example.mcp.server.api.dto.ArithmeticResponse;
import com.example.mcp.server.service.CalculatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 四则运算的 REST 接口。与 MCP 工具共用同一个 CalculatorService。
 */
@RestController
@RequestMapping("/api/v1/calculator")
@Tag(name = "四则运算", description = "加减乘除 REST 接口")
public class CalculatorController {

    private final CalculatorService calculatorService;

    public CalculatorController(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @Operation(summary = "加法", description = "返回 a + b")
    @GetMapping("/add")
    public ArithmeticResponse add(
            @Parameter(description = "第一个加数", example = "10") @RequestParam double a,
            @Parameter(description = "第二个加数", example = "5") @RequestParam double b) {
        return new ArithmeticResponse("add", a, b, calculatorService.add(a, b));
    }

    @Operation(summary = "减法", description = "返回 a - b")
    @GetMapping("/subtract")
    public ArithmeticResponse subtract(
            @Parameter(description = "被减数", example = "10") @RequestParam double a,
            @Parameter(description = "减数", example = "5") @RequestParam double b) {
        return new ArithmeticResponse("subtract", a, b, calculatorService.subtract(a, b));
    }

    @Operation(summary = "乘法", description = "返回 a * b")
    @GetMapping("/multiply")
    public ArithmeticResponse multiply(
            @Parameter(description = "第一个乘数", example = "10") @RequestParam double a,
            @Parameter(description = "第二个乘数", example = "5") @RequestParam double b) {
        return new ArithmeticResponse("multiply", a, b, calculatorService.multiply(a, b));
    }

    @Operation(summary = "除法", description = "返回 a / b；除数为 0 时返回 400")
    @GetMapping("/divide")
    public ArithmeticResponse divide(
            @Parameter(description = "被除数", example = "10") @RequestParam double a,
            @Parameter(description = "除数，不能为 0", example = "5") @RequestParam double b) {
        return new ArithmeticResponse("divide", a, b, calculatorService.divide(a, b));
    }
}