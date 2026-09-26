# AGENTS.md

给 AI 编码助手 / 新接手同事的**操作手册**：怎么构建、怎么启动、怎么调试、参数在哪儿改。

原理层面的讲解（MCP 是什么、为什么这么设计）看 [README.md](README.md)，本文件只讲操作。

---

## 1. 项目速览

| 模块 | 端口 | 角色 |
|---|---|---|
| `calculator-mcp-server` | 8080 | MCP Server（4 个 `@McpTool`）+ REST API + Swagger |
| `calculator-mcp-client` | 8081 | MCP Client（SSE 连服务端）+ DeepSeek 对话入口 |

两个模块必须**分别启动**，客户端依赖服务端。业务真源只有一个：`CalculatorService`。

---

## 2. 环境要求

| 项 | 要求 | 检查命令 |
|---|---|---|
| JDK | 25（`maven.compiler.release=25`，低版本编译直接失败） | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| 环境变量 | 客户端必需 `DEEPSEEK_API_KEY`（服务端不需要） | `$env:DEEPSEEK_API_KEY` |

设置 API Key（PowerShell）：

```powershell
$env:DEEPSEEK_API_KEY = "sk-xxxxxxxxxxxxxxxx"          # 仅当前会话
[Environment]::SetEnvironmentVariable("DEEPSEEK_API_KEY", "sk-xxxxxxxxxxxxxxxx", "User")   # 永久
```

---

## 3. 构建与启动

### 3.1 构建

```bash
mvn clean package
```

产物：两个可执行 jar。首次构建会下载依赖，耗时较长。

> 只想构建一个模块：`mvn -pl calculator-mcp-server -am clean package`

### 3.2 启动（顺序不能反）

```bash
# 终端 1：必须先行，且必须等到 8080 就绪
java -jar calculator-mcp-server/target/calculator-mcp-server-1.0.0.jar

# 终端 2：服务端起来之后再启动
java -jar calculator-mcp-client/target/calculator-mcp-client-1.0.0.jar
```

**顺序不能反**：客户端的 MCP 连接在 Bean 创建阶段就发起（`spring.ai.mcp.client.initialized` 默认 `true`）。服务端没起来 → 客户端启动失败退出。

开发期只想跑其中一个时：

```bash
mvn -pl calculator-mcp-server spring-boot:run
mvn -pl calculator-mcp-client spring-boot:run
```

### 3.3 停止

两个进程都在前台运行，`Ctrl+C` 即可。若在后台跑，按端口杀：

```powershell
netstat -ano | findstr :8080     # 拿到 PID
Stop-Process -Id <PID> -Force
```

---

## 4. 配置参数

配置文件位置：`<模块>/src/main/resources/application.yml`

### 4.1 服务端 `calculator-mcp-server`

| 参数 | 默认值 | 作用 |
|---|---|---|
| `server.port` | `8080` | HTTP 端口 |
| `spring.application.name` | `calculator-mcp-server` | 应用名 |
| `spring.ai.mcp.server.name` | `calculator-mcp-server` | 返回给客户端的 `serverInfo.name` |
| `spring.ai.mcp.server.version` | `1.0.0` | `serverInfo.version` |
| `spring.ai.mcp.server.protocol` | `SSE` | 传输协议：`SSE` / `STREAMABLE` / `STATELESS` |
| `spring.ai.mcp.server.sse-endpoint` | `/sse` | SSE 长连接端点（客户端从这里拿 sessionId） |
| `spring.ai.mcp.server.sse-message-endpoint` | `/mcp/message` | 接收 JSON-RPC 请求的端点 |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI 路径 |

### 4.2 客户端 `calculator-mcp-client`

| 参数 | 默认值 | 作用 |
|---|---|---|
| `server.port` | `8081` | HTTP 端口 |
| `spring.ai.deepseek.api-key` | `${DEEPSEEK_API_KEY}` | **必填**，环境变量缺失会启动失败 |
| `spring.ai.mcp.client.name` | `calculator-mcp-client` | 客户端标识 |
| `spring.ai.mcp.client.version` | `1.0.0` | 客户端版本 |
| `spring.ai.mcp.client.type` | `SYNC` | 同步客户端（另有 `ASYNC`） |
| `spring.ai.mcp.client.sse.connections.<名字>.url` | `http://localhost:8080` | 服务端基地址，**不含** `/sse` |
| `spring.ai.mcp.client.sse.connections.<名字>.sse-endpoint` | `/sse` | 与服务端 `sse-endpoint` 保持一致 |

> `spring.ai.mcp.client.initialized` 默认 `true`：启动即连接并拉取工具列表。
> 想让客户端懒连接，设为 `false`，首次调用时才建连。

### 4.3 临时改参数（不改文件）

命令行参数优先级高于 `application.yml`：

```bash
java -jar calculator-mcp-server/target/calculator-mcp-server-1.0.0.jar --server.port=9090
java -jar calculator-mcp-client/target/calculator-mcp-client-1.0.0.jar --spring.ai.mcp.client.sse.connections.calculator-server.url=http://localhost:9090
```

环境变量写法（等价）：`SPRING_AI_MCP_SERVER_PROTOCOL=STREAMABLE`

### 4.4 切换到 Streamable HTTP（官方推荐）

服务端三行改一行：

```yaml
spring.ai.mcp.server.protocol: STREAMABLE
# 下面两行删掉（Streamable 只有一个端点 /mcp）
# sse-endpoint: /sse
# sse-message-endpoint: /mcp/message
```

客户端同步改：

```yaml
spring.ai.mcp.client:
  streamable-http:
    connections:
      calculator-server:
        url: http://localhost:8080
```

依赖不用动。注意端点变成 `POST /mcp`（不再是 `/sse` + `/mcp/message`）。

---

## 5. 调试

### 5.1 先看这两行日志确认链路通

| 端 | 关键字 | 含义 |
|---|---|---|
| 服务端 | `Registered tools: 4` | 4 个 `@McpTool` 被扫描注册成功 |
| 服务端 | `Tomcat started on port 8080` | HTTP 端口就绪 |
| 客户端 | `Server response with Protocol: 2024-11-05 ... Info: Implementation[name=calculator-mcp-server...]` | MCP 握手成功，工具已拉到 |

客户端启动时这两条 WARN 是**无害**的，不用管：`No sampling methods found`、`No elicitation methods found`（服务端没实现采样/征询能力）。

### 5.2 手工探测 MCP 协议（绕过 LLM，直接验协议）

PowerShell 里 `curl` 是 `Invoke-WebRequest` 的别名，**必须写 `curl.exe`**：

```powershell
# 1. 长连接，会立刻收到 event:endpoint 和 sessionId
curl.exe -N http://localhost:8080/sse

# 2. 另开窗口，用上一步的 sessionId 发 JSON-RPC
curl.exe -X POST "http://localhost:8080/mcp/message?sessionId=<SESSION_ID>" `
  -H "Content-Type: application/json" `
  -d '{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}'

# 3. 调工具
curl.exe -X POST "http://localhost:8080/mcp/message?sessionId=<SESSION_ID>" `
  -H "Content-Type: application/json" `
  -d '{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"add\",\"arguments\":{\"a\":12,\"b\":8}}}'
```

响应不直接回 POST，而是**从第 1 步那条 SSE 连接推回来**，所以要盯着第 1 个窗口看。

### 5.3 验证「模型真的调了工具」还是自己心算

在 [CalculatorMcpTools.java](calculator-mcp-server/src/main/java/com/brooks/mcp/server/mcp/CalculatorMcpTools.java) 或 `CalculatorService` 的方法里打断点 / 加一行日志。发起一次对话请求，如果断点命中 → 走的是 MCP 工具链路；没命中 → 模型自己口算了，检查客户端 system prompt。

### 5.4 跑测试

```bash
mvn -pl calculator-mcp-server test        # CalculatorServiceTest 6 个 + CalculatorControllerTest 4 个
mvn -pl calculator-mcp-server test -Dtest=CalculatorServiceTest    # 只跑一个类
```

客户端模块**没有测试**，这是有意的（它依赖外部 LLM）。

### 5.5 常用调试点

| 想看什么 | 在哪儿断 |
|---|---|
| 工具入参/出参 | `CalculatorMcpTools.*` |
| 业务逻辑 | `CalculatorService.*` |
| 模型有没有下发 tool_calls | `CalculatorChatService.chat` |

---

## 6. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 客户端启动即退出，日志有连接拒绝 | 服务端没起或没起完。先起 8080，等到 Tomcat 就绪 |
| 客户端报 `api-key` 相关错误 | `DEEPSEEK_API_KEY` 未设置，或设置的终端不是启动 jar 的那个终端 |
| 端口被占用 | `netstat -ano \| findstr :8080` 找 PID 后杀掉，或用 `--server.port=` 换端口 |
| 服务端日志没有 `Registered tools` | 工具类缺 `@Component`，或不在 `com.brooks.mcp.server` 包扫描路径下 |
| 日志提示 SSE 已 deprecated | 预期内。Spring AI 2.0.0 起 SSE 标记废弃，本项目为兼容 Spring AI 1.0 客户端而保留 |
| 模型答对了但没调工具 | 改客户端 system prompt，明确要求「必须调用计算器工具，不要心算」 |

---

## 7. 开发约定

**新增一个 MCP 工具**：在 `CalculatorMcpTools` 加一个 `@McpTool` 方法即可，重启后 MCP 与 Swagger 自动生效。

```java
@McpTool(name = "power", description = "计算 a 的 b 次方")
public double power(
        @McpToolParam(description = "底数", required = true) double a,
        @McpToolParam(description = "指数", required = true) double b) {
    return calculatorService.power(a, b);   // 业务逻辑写在 Service，MCP 层保持薄壳
}
```

**给现有工具加参数**：加一个带 `@McpToolParam` 的形参即可，会同步进 `inputSchema`。注意 `description` 是写给**模型**看的（模型靠它决定怎么传参），不是写给人看的注释。

**约定**：

1. 业务逻辑一律放 `CalculatorService`，MCP / REST 两层只做参数翻译与转发。
2. MCP 工具的自定义异常**必须继承 `RuntimeException`**——这样才会以 `isError: true` 的 tool result 回给模型（模型可自愈）；checked exception 会变成协议级失败。
3. `@McpTool` 的 `name` 用 snake_case 或单词小写，`description` 写清「做什么 + 返回什么」。
4. 服务端写测试用 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + JDK 原生 `HttpClient`，**不要用 `@WebMvcTest`**（Spring Boot 4 中该类注解已迁移到独立 test 模块）。
5. 合并前跑 `mvn clean package`，服务端 10 个测试必须全绿。