package com.brooks.mcp.client.api;

import com.brooks.mcp.client.api.dto.ChatRequest;
import com.brooks.mcp.client.api.dto.ChatResponse;
import com.brooks.mcp.client.chat.CalculatorChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "MCP 对话", description = "用自然语言提问，模型会自动调用远端 MCP 计算器工具")
public class ChatController {

    private final CalculatorChatService chatService;

    public ChatController(CalculatorChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Operation(summary = "自然语言对话", description = "例如：帮我算一下 (12 + 8) * 3 等于多少")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return new ChatResponse(chatService.chat(request.message()));
    }
}