package com.brooks.mcp.server.service;

import com.brooks.mcp.server.exception.DivisionByZeroException;
import org.springframework.stereotype.Service;

/**
 * 四则运算业务逻辑。被 REST 层和 MCP 工具层共同复用。
 */
@Service
public class CalculatorService {

    public double add(double a, double b) {
        return a + b;
    }

    public double subtract(double a, double b) {
        return a - b;
    }

    public double multiply(double a, double b) {
        return a * b;
    }

    public double divide(double a, double b) {
        if (b == 0) {
            throw new DivisionByZeroException("除数不能为 0");
        }
        return a / b;
    }
}