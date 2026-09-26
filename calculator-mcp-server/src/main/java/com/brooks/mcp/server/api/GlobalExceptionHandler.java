package com.brooks.mcp.server.api;

import com.brooks.mcp.server.exception.DivisionByZeroException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * 把业务异常翻译成标准的 RFC 9457 ProblemDetail 响应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DivisionByZeroException.class)
    public ProblemDetail handleDivisionByZero(DivisionByZeroException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("非法运算");
        problem.setType(URI.create("https://example.com/problems/division-by-zero"));
        return problem;
    }
}