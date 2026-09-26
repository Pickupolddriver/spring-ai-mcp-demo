package com.brooks.mcp.server.exception;

/**
 * 除数为 0 时抛出。
 * <p>继承 RuntimeException 是有意为之：MCP 侧 Spring AI 会把 RuntimeException
 * 作为 error result 返回给模型（模型可自行纠错），而不是让整个调用失败。</p>
 */
public class DivisionByZeroException extends RuntimeException {

    public DivisionByZeroException(String message) {
        super(message);
    }
}