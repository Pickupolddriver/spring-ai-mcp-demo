# spring-ai-mcp-calculator

Spring Boot 4 + Spring AI 2 + MCP（Model Context Protocol）四则运算 Demo。

同一个 `CalculatorService` 同时以两种方式对外暴露：

- **REST**：`GET /api/v1/calculator/{add|subtract|multiply|divide}`，配 Swagger UI
- **MCP**：`add` / `subtract` / `multiply` / `divide` 四个工具，通过 Streamable HTTP 暴露给任意 MCP 客户端

另有一个 MCP Client 模块，用 DeepSeek 驱动，把「自然语言问题」翻译成「调用远端 MCP 工具」。

---

## 1. 技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| Java | 25 | `maven.compiler.release=25` |
| Spring Boot | 4.0.8 | 注意 starter 改名，见 §5 |
| Spring AI | 2.0.1 | 通过 `spring-ai-bom` 统一版本 |
| springdoc-openapi | 3.1.1 | 官方声明支持 Spring Boot v4 |
| MCP 协议 | 2025-11-25 | 客户端与服务端协商出的版本 |

---

## 2. 模块结构

```
spring-ai-mcp-calculator/          父 POM（packaging=pom，聚合两个模块）
├── calculator-mcp-server/           端口 8080 —— MCP Server + REST API + Swagger
│   └── src/main/java/com/example/mcp/server/
│       ├── CalculatorMcpServerApplication.java   启动类
│       ├── service/CalculatorService.java        四则运算业务逻辑（唯一真源）
│       ├── mcp/CalculatorMcpTools.java           @McpTool 工具定义
│       ├── api/CalculatorController.java         REST 接口
│       ├── api/GlobalExceptionHandler.java       RFC 9457 错误响应
│       ├── api/dto/ArithmeticResponse.java       REST 响应体
│       ├── config/OpenApiConfig.java             Swagger 元信息
│       └── exception/DivisionByZeroException.java
└── calculator-mcp-client/           端口 8081 —— MCP Client + DeepSeek 对话
    └── src/main/java/com/example/mcp/client/
        ├── CalculatorMcpClientApplication.java   启动类
        ├── chat/CalculatorChatService.java       ChatClient + MCP 工具
        ├── api/ChatController.java               POST /api/v1/chat
        ├── api/dto/ChatRequest.java
        └── config/OpenApiConfig.java
```

同一个 `CalculatorService`，两条对外暴露路径：

```mermaid
graph LR
    REST["REST 调用方<br/>curl · Swagger UI"] --> CTRL["CalculatorController<br/>GET /api/v1/calculator/*"]
    MCPC["MCP 调用方<br/>calculator-mcp-client<br/>Claude Desktop 等"] -->|"Streamable HTTP · JSON-RPC"| TOOLS["CalculatorMcpTools<br/>@McpTool × 4"]
    CTRL --> SVC["CalculatorService<br/>add / subtract / multiply / divide"]
    TOOLS --> SVC
```

**为什么拆成两个进程？**
MCP 的本质是「客户端通过协议访问独立进程的服务端」。如果把 Client 和 Server 塞进同一个 JVM，Client 必须在 Server 的 HTTP 端点就绪后才能连接，而 `spring.ai.mcp.client.initialized` 默认为 `true`，意味着 Bean 创建阶段就会去 `initialize()` 拉工具列表——此时 MVC 的 MCP 端点处理器可能还没注册。这是一个真实的启动竞态。拆成两个进程既绕开了竞态，也忠实还原了 MCP 的跨进程架构。

---

## 3. 快速开始

### 3.1 构建

```bash
mvn clean package
```

预期：两个模块 `BUILD SUCCESS`，服务端 10 个测试全部通过。

### 3.2 配置 API Key

客户端需要 DeepSeek API Key，通过环境变量注入：

```powershell
# PowerShell（当前会话生效）
$env:DEEPSEEK_API_KEY = "sk-xxxxxxxxxxxxxxxx"

# 永久写入用户环境变量
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-xxxxxxxxxxxxxxxx", "User")
```

### 3.3 启动（需要两个终端，先起服务端）

```bash
# 终端 1
java -jar calculator-mcp-server/target/calculator-mcp-server-1.0.0.jar

# 终端 2
java -jar calculator-mcp-client/target/calculator-mcp-client-1.0.0.jar
```

启动成功的标志：服务端日志出现 `Registered tools: 4`；客户端日志出现
`Server response with Protocol: 2025-11-25 ... Info: Implementation[name=calculator-mcp-server...]`。

### 3.4 访问入口

| 用途 | 地址 |
|---|---|
| 服务端 Swagger UI | http://localhost:8080/swagger-ui.html |
| 服务端 OpenAPI JSON | http://localhost:8080/v3/api-docs |
| 客户端 Swagger UI | http://localhost:8081/swagger-ui.html |
| 流式对话演示页 | http://localhost:8081/ |
| MCP 端点（Streamable HTTP） | `POST` http://localhost:8080/mcp |

---

## 4. 验证

### 4.1 REST 接口

```bash
curl "http://localhost:8080/api/v1/calculator/add?a=12&b=8"
# {"operation":"add","a":12.0,"b":8.0,"result":20.0}

curl "http://localhost:8080/api/v1/calculator/divide?a=1&b=0"
# HTTP 400
# {"detail":"除数不能为 0","instance":"/api/v1/calculator/divide",
#  "status":400,"title":"非法运算","type":"https://example.com/problems/division-by-zero"}
```

### 4.2 MCP 协议（手工走一遍 JSON-RPC）

Streamable HTTP 只有一个端点 `POST /mcp`。会话 ID 不再放在 URL 查询参数里，而是由 `initialize` 通过**响应头** `Mcp-Session-Id` 下发；后续每个请求带上这个头即可。响应也从那条长连接上「推回来」变成**由该次 POST 直接返回**。

```bash
# 第 1 步：initialize，从响应头拿 Mcp-Session-Id
curl -i -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{
       "protocolVersion":"2025-11-25","capabilities":{},
       "clientInfo":{"name":"manual","version":"1.0.0"}}}'

# HTTP/1.1 200
# Mcp-Session-Id: <SESSION_ID>
# {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25",
#   "capabilities":{"tools":{"listChanged":true},...},
#   "serverInfo":{"name":"calculator-mcp-server","version":"1.0.0"}}}
```

> `Accept` 必须同时包含 `application/json` 与 `text/event-stream`，否则服务端拒绝。这一步返回的是**裸 JSON**，下一步返回的却是 **SSE 帧**——协议允许两者，所以调用方要给两种响应体都留出路。

```bash
# 第 2 步：拉工具列表
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: <SESSION_ID>" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

# id:<SESSION_ID>
# event:message
# data:{"jsonrpc":"2.0","id":2,"result":{"tools":[
#   {"name":"add","title":"加法","description":"计算两个数之和，返回 a + b",
#    "inputSchema":{"$schema":"https://json-schema.org/draft/2020-12/schema",
#      "type":"object","properties":{
#        "a":{"type":"number","format":"double","description":"第一个加数"},
#        "b":{"type":"number","format":"double","description":"第二个加数"}},
#      "required":["a","b"]},
#    "annotations":{"title":"加法","readOnlyHint":true,"destructiveHint":false,
#                   "idempotentHint":true,"openWorldHint":false}},
#   ... 共 4 个工具
# ]}}
```

`annotations` 是写给客户端的**安全提示**。四个运算都是纯函数，所以 `readOnlyHint: true`（不修改任何状态）、`idempotentHint: true`（重复调用结果一致）、`destructiveHint: false`、`openWorldHint: false`（不触碰外部世界）。Host 据此判断该工具能不能静默执行、要不要弹窗让用户二次确认。

```bash
# 第 3 步：调用工具
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -H "Mcp-Session-Id: <SESSION_ID>" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call",
       "params":{"name":"add","arguments":{"a":12,"b":8}}}'

# event:message
# data:{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"20.0"}],"isError":false}}
```

除零时走错误分支（`isError: true`，不会抛协议级异常）：

```json
{"jsonrpc":"2.0","id":4,"result":{"content":[{"type":"text","text":"除数不能为 0"}],"isError":true}}
```

### 4.3 LLM 调用 MCP 工具（完整链路）

```bash
curl -N -X POST http://localhost:8081/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"计算 100 除以 8"}'
# 响应是 SSE（text/event-stream），-N 关掉缓冲才能看到逐块到达：
# data:100
# data: ÷ 8
# data: = 12.5
```

> PowerShell 下这条命令不能照抄：`-d` 里的双引号会被 shell 吃掉（报 `JSON parse error: Unexpected character`），把 body 写成 UTF-8 文件再 `curl.exe --data-binary "@file"` 最稳，见 [AGENTS.md](AGENTS.md) §5.2。

这条请求背后发生的完整链路：

```mermaid
sequenceDiagram
    autonumber
    participant U as 调用方<br/>curl -N
    participant C as 客户端 :8081<br/>ChatController
    participant D as DeepSeek 模型
    participant M as MCP Client<br/>(同进程)
    participant S as 服务端 :8080<br/>CalculatorMcpTools
    participant V as CalculatorService

    U->>C: POST /api/v1/chat  计算 100 除以 8
    C->>D: 消息 + 4 个工具定义
    D-->>C: tool_calls: divide(100, 8)
    C->>M: 执行工具调用
    M->>S: tools/call divide(100, 8)
    S->>V: divide(100, 8)
    V-->>S: 12.5
    S-->>M: result: 12.5（JSON-RPC 响应直接由该次 POST 返回）
    M-->>C: 工具结果
    C->>D: 带上工具结果继续对话
    D-->>C: 100 ÷ 8 = 12.5
    C-->>U: data: 100 ÷ 8 = 12.5（SSE 逐块）
```

> 流式之下，第 2~4 步（模型生成 `tool_calls`、执行工具）期间调用方**收不到任何内容**，DeepSeek 只在最后一轮才开始逐块推送文字增量。所以实际观感是「先静默一小段，再开始出字」。

---

## 5. 关键知识点（首次接触 MCP 必读）

### 5.1 MCP 是什么

MCP（Model Context Protocol）是 Anthropic 提出的开放协议，把「模型能用什么能力」标准化成一套 JSON-RPC 契约。核心角色：

- **Server**：声明自己有哪些 **Tools / Resources / Prompts**，等待被调用
- **Client**：连接到 Server，拉取能力清单，替模型执行工具调用
- **Host**：真正跑模型的应用（Claude Desktop、IDE、你自己的 Spring Boot 服务）

本 Demo 里，`calculator-mcp-server` 是 Server，`calculator-mcp-client` 同时扮演 Client，DeepSeek 是模型。

好处是**解耦**：工具提供方不需要知道调用方是谁、用的是哪家模型；换成 OpenAI / Ollama / Claude 只改客户端一行依赖，服务端完全不动。

### 5.2 服务端：一个注解就够了

[CalculatorMcpTools.java](calculator-mcp-server/src/main/java/com/example/mcp/server/mcp/CalculatorMcpTools.java) 的全部工作量就是加注解：

```java
@Component
public class CalculatorMcpTools {

    @McpTool(name = "add", title = "加法", description = "计算两个数之和，返回 a + b",
            annotations = @McpTool.McpAnnotations(title = "加法", readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true, openWorldHint = false))
    public double add(
            @McpToolParam(description = "第一个加数", required = true) double a,
            @McpToolParam(description = "第二个加数", required = true) double b) {
        return calculatorService.add(a, b);
    }
    // subtract / multiply / divide 同理
}
```

- `@McpTool` 的 `description` 极其重要——**模型就是靠这段文字决定要不要调用这个工具**。写给人看的注释和写给模型看的 description 是两件事。
- `name` 是给协议和代码用的标识（客户端按它发起 `tools/call`），`title` 是给人看的显示名。MCP 里 `title` 有两处（工具级、annotations 内），是同一语义的两个独立字段，所以两处都写。
- 参数上的 `@McpToolParam` 会被翻译成 JSON Schema 的 `inputSchema`，模型据此生成结构化参数。
- `annotations` 里的四个 hint 会随 `tools/list` 下发给客户端，是**给 Host 的安全元数据**（能不能静默执行、要不要弹窗确认）。这四个运算都是纯函数，所以 `readOnlyHint` / `idempotentHint` 为 `true`，另两个为 `false`。
- 加了 `@Component`，Spring AI 的注解扫描器在启动时自动注册，日志里就能看到 `Registered tools: 4`。
- Spring AI 2 里注解在包 `org.springframework.ai.mcp.annotation`。

### 5.3 异常处理：RuntimeException vs checked exception

`CalculatorService.divide` 在除数为 0 时抛 `DivisionByZeroException extends RuntimeException`。这个选择是有意的：

- **`RuntimeException` 子类** → MCP 侧捕获后作为 `isError: true` 的 **tool result** 回给模型。模型能看到「除数不能为 0」并自己组织措辞，甚至重试。这是期望行为。
- **checked exception** → 直接让工具调用失败，模型拿到的是一个协议错误，通常无法自愈。

所以给 MCP 工具做业务校验时，**自定义异常请继承 `RuntimeException`**。

### 5.4 传输协议：Streamable HTTP（本项目）vs HTTP+SSE（旧）

| | HTTP+SSE（旧） | Streamable HTTP（本项目） |
|---|---|---|
| 端点 | `GET /sse`（长连接）+ `POST /mcp/message`（发请求） | 单端点 `POST /mcp` |
| 会话 | `sessionId` 查询参数，必须先开长连接才拿得到 | `Mcp-Session-Id` 请求头，由 `initialize` 响应头下发 |
| 响应 | 从 SSE 长连接推回来 | 由该次 POST 直接返回 |
| 状态 | Spring AI 2.0.0 起 **已 deprecated** | 官方推荐 |
| 协议版本 | **只支持 2024-11-05**，把版本钉死 | 可协商到 SDK 支持的最高版（本项目实测 **2025-11-25**） |
| 兼容性 | Spring AI 1.0 客户端只认它 | 需要较新的客户端 |

**本项目用 Streamable HTTP**，服务端只需两行：

```yaml
spring.ai.mcp.server.protocol: STREAMABLE
spring.ai.mcp.server.streamable-http.mcp-endpoint: /mcp
```

注意 `mcp-endpoint` 默认就是 `/mcp`，写出来只是为了让端点显式可见。客户端同步用 `streamable-http.connections.<name>.url`（不再有 `sse-endpoint`）。

**为什么不保留 SSE**：MCP 规范 2026-07-28 修订已正式废弃 HTTP+SSE（给出一年过渡期），且它把协议版本锁死在 2024-11-05——切到 Streamable 后客户端握手直接协商到 2025-11-25，跨了三个修订。**依赖不用动**——`spring-ai-starter-mcp-server-webmvc` 同时支持 SSE / STREAMABLE / STATELESS 三种协议。

真要用回 SSE（比如对接一个只认 SSE 的 Spring AI 1.0 客户端）：

```yaml
spring.ai.mcp.server.protocol: SSE
spring.ai.mcp.server.sse-endpoint: /sse
spring.ai.mcp.server.sse-message-endpoint: /mcp/message
```

客户端换成 `spring.ai.mcp.client.sse.connections.<name>.{url,sse-endpoint}` 即可。代价是协议版本退回 2024-11-05。

### 5.5 客户端：ChatClient + ToolCallbackProvider

[CalculatorChatService.java](calculator-mcp-client/src/main/java/com/example/mcp/client/chat/CalculatorChatService.java)：

```java
this.chatClient = builder
        .defaultSystem("你是一个计算助手。遇到加减乘除时，必须调用提供的计算器工具，不要自己心算。")
        .build();
```

`ToolCallbackProvider` 由 MCP Client 自动配置注入，里面装的就是从 `calculator-mcp-server` 拉回来的 4 个工具。调用的关键一行是 `.tools(mcpTools)`：

```java
chatClient.prompt().user(message).tools(mcpTools).stream().content();   // Flux<String>
```

这里用 `.stream()` 而不是 `.call()`：DeepSeek 以 SSE 逐块推回文字增量，`ChatController` 再以 `text/event-stream` 原样转发给调用方。代价是响应契约从 JSON 变成了 SSE——Swagger UI 的 Try it out 显示不了流式响应，得用 `curl -N` 验证。

System prompt 里那句「必须调用工具，不要自己心算」不是客套话——**大模型对简单算术倾向于直接口算**，而口算会出错。把工具调用写进 system prompt 是让 Demo 稳定演示工具链路的必要手段。

### 5.6 Spring Boot 4 带来的坑（升级时最容易踩）

1. **starter 改名**
   | Boot 3 | Boot 4 |
   |---|---|
   | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
   | `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |

2. **测试注解包路径迁移**。`@WebMvcTest` 等注解搬到了新的 test 模块（如 `spring-boot-webmvc-test`）。
   本项目为规避这一点，`CalculatorControllerTest` 刻意不用 `@WebMvcTest` / `TestRestTemplate`，而是 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + JDK 原生 `HttpClient` 发真实 HTTP 请求——顺带也验证了完整的 Servlet 栈。

3. **默认 Jackson 3**。Boot 4 的 JSON 序列化器从 Jackson 2 换成 Jackson 3，包名由 `com.fasterxml.jackson.*` 变成 `tools.jackson.*`，groupId 也从 `com.fasterxml.jackson` 改为 `tools.jackson`（`jackson-annotations` 是唯一例外，坐标不变）。import 时别再惯性写 `com.fasterxml`。

4. **Spring AI 2 移除了配置里的 `.options` 段**。所以本项目的 `application.yml` 只写 `spring.ai.deepseek.api-key`，不再有 `spring.ai.deepseek.chat.options.model` 这类配置。

---

## 6. 如何扩展

**加一个新工具**：在 `CalculatorMcpTools` 里加一个 `@McpTool` 方法即可，重启后 MCP 和 Swagger 都是自动的。

**换成别的模型**：改客户端 pom 依赖（`spring-ai-starter-model-openai` / `-ollama` / `-anthropic`）+ `application.yml` 里的 key，`CalculatorChatService` 一行不用改。

**把工具接到 Claude Desktop 等真实 Host**：在 Host 配置里写 Streamable HTTP 端点 `http://localhost:8080/mcp` 即可，本项目已经手工验证过协议交互是标准兼容的。

**接到真实业务**：把 `CalculatorService` 换成真实的领域服务，MCP 层保持只做「参数翻译 + 转发」的薄壳。