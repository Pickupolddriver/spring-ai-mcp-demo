package com.brooks.mcp.client.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "对话响应")
public record ChatResponse(
        @Schema(description = "模型最终回答", example = "结果是 60")
        String answer) {
}