package com.example.mcp.client.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "对话请求")
public record ChatRequest(
        @Schema(description = "自然语言问题", example = "帮我算一下 (12 + 8) * 3 等于多少")
        String message) {
}