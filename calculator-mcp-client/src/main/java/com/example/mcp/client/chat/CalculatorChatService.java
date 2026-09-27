package com.example.mcp.client.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;

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

    /**
     * 流式对话：逐块返回模型生成的内容。
     * <p>调用工具的那一轮不会有文本输出——Spring AI 会在内部聚合 tool call、执行 MCP 工具，
     * 再发起下一轮请求，之后才开始推送最终回答的文字增量。</p>
     */
    public Flux<String> chatStream(String message) {
        return chatClient.prompt()
                .user(message)
                .tools(mcpTools)
                .stream()
                .content();
    }
}