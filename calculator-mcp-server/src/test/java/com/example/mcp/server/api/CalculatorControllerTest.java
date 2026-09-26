package com.example.mcp.server.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Web 层集成测试：启动真实端口，用 JDK HttpClient 发请求。
 * 刻意不使用 TestRestTemplate / @WebMvcTest —— Spring Boot 4 模块化后它们的包路径有变动风险。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalculatorControllerTest {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void add_returnsSum() throws Exception {
        HttpResponse<String> response = get("/api/v1/calculator/add?a=10&b=5");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"result\":15.0");
    }

    @Test
    void multiply_returnsProduct() throws Exception {
        HttpResponse<String> response = get("/api/v1/calculator/multiply?a=10&b=5");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"result\":50.0");
    }

    @Test
    void divide_byZero_returns400() throws Exception {
        HttpResponse<String> response = get("/api/v1/calculator/divide?a=1&b=0");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("除数不能为 0");
    }

    @Test
    void openApiDocs_areAvailable() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Calculator MCP Server API");
    }
}