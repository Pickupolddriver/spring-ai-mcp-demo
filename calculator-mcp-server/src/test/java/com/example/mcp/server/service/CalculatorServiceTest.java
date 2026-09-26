package com.example.mcp.server.service;

import com.example.mcp.server.exception.DivisionByZeroException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 纯业务逻辑单测，不加载 Spring 容器。
 */
class CalculatorServiceTest {

    private final CalculatorService service = new CalculatorService();

    @Test
    void add_returnsSum() {
        assertThat(service.add(10, 5)).isEqualTo(15.0);
    }

    @Test
    void add_withNegativeNumber() {
        assertThat(service.add(-10, 5)).isEqualTo(-5.0);
    }

    @Test
    void subtract_returnsDifference() {
        assertThat(service.subtract(10, 5)).isEqualTo(5.0);
    }

    @Test
    void multiply_returnsProduct() {
        assertThat(service.multiply(10, 5)).isEqualTo(50.0);
    }

    @Test
    void divide_returnsQuotient() {
        assertThat(service.divide(10, 5)).isEqualTo(2.0);
    }

    @Test
    void divide_byZero_throws() {
        assertThatThrownBy(() -> service.divide(1, 0))
                .isInstanceOf(DivisionByZeroException.class)
                .hasMessage("除数不能为 0");
    }
}