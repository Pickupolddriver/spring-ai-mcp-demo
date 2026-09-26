package com.brooks.mcp.server.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "四则运算结果")
public record ArithmeticResponse(
        @Schema(description = "运算类型", example = "add") String operation,
        @Schema(description = "第一个操作数", example = "10") double a,
        @Schema(description = "第二个操作数", example = "5") double b,
        @Schema(description = "运算结果", example = "15") double result) {
}