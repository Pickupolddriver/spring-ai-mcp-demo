package com.brooks.mcp.client.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

/**
 * 把「DeepSeek 模型」和「远端 MCP 工具」组合起来。
 * <p>ToolCallbackProvider 由 Spring AI 的 MCP Client 自动配置提供，
 * 其中包含了从 calculator-mcp-server 拉取到的 4 个工具。</p>
 */
@Service
public class CalculatorChatService {

    private final ChatClient chatClient;
    private final ToolCallbackProvider mcpTools;

    public CalculatorChatService(ChatClient.Builder builder, ToolCallbackProvider mcpTools) {
        this.chatClient = builder
                .defaultSystem("你是一个计算助手。遇到加减乘除时，必须调用提供的计算器工具，不要自己心算。")
                .build();
        this.mcpTools = mcpTools;
    }

    public String chat(String message) {
        return chatClient.prompt()
                .user(message)
                .tools(mcpTools)
                .call()
                .content();
    }
}