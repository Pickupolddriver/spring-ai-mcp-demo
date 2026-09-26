# Spring Boot 4 + Spring AI 2 + MCP 四则运算 Demo — 实施计划

## 1. 概述（Summary）

在空目录 `d:\CodeRepo\RAG\brooks-mcp-demo` 中，从零建立一个 **Git + Maven 多模块** 的 Spring Boot 4 / Spring AI 2 项目，交付一个「MCP Server 计算器」Demo，包含：

1. **MCP Server**：通过 HTTP + SSE 暴露 4 个 MCP 工具（add / subtract / multiply / divide）
2. **REST API**：同样的四则运算能力，走普通 HTTP 接口
3. **Swagger UI**：可视化调试页面
4. **MCP Client + LLM 对话**：用 DeepSeek 驱动，自然语言 → 自动调用 MCP 计算器工具
5. **单元测试**：覆盖四则运算与除零边界
6. **讲解文档**：开发完成后在对话中给出完整讲解，并落一份 `README.md`

---

## 2. 现状分析（Current State Analysis）

已实际核查，而非假设：

| 项 | 结论 |
|---|---|
| 工作目录 | `d:\CodeRepo\RAG\brooks-mcp-demo` **完全为空**，且 **不是 git 仓库** |
| Java | `openjdk 25.0.4.1 LTS`（Amazon Corretto，`C:\Program Files\Amazon Corretto\jdk25.0.4_8`）✅ |
| Maven | `Apache Maven 3.9.7` ✅ |
| Git | `git version 2.54.0.windows.1` ✅ |
| 历史约定 | 无任何历史代码 / memory，属于全新项目，无需兼容既有风格 |

> 备注：Maven 启动时会打印 `WARNING: A restricted method in java.lang.System has been called`（jansi 调用 `System::load`）。这是 Maven 自身在 JDK 25 下的原生访问警告，**不影响构建**，无需处理。

---

## 3. 技术选型（全部已核实存在于 Maven Central）

| 组件 | 版本 | 核实方式 |
|---|---|---|
| Java | **25** | 本机 Corretto 25.0.4.1 |
| Spring Boot | **4.0.8** | `spring-boot-starter-parent` maven-metadata：4.0.x 最新补丁 |
| Spring AI | **2.0.1** | `spring-ai-bom` maven-metadata：最新 GA（2.1.0-M1 为里程碑版，不采用） |
| springdoc-openapi | **3.1.1** | 官方文档声明支持 `Spring-boot v4` |
| LLM | **DeepSeek** | `spring-ai-starter-model-deepseek` 已在 2.0.1 BOM 中列出 |
| MCP 传输 | **SSE**（HTTP + SSE） | 见 §4.1 |

### 3.1 关键依赖坐标（已逐个在 2.0.1 BOM / Maven Central 中确认）

- `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` ✅
- `org.springframework.ai:spring-ai-starter-mcp-client` ✅（标准客户端，SSE 走 JDK HttpClient）
- `org.springframework.ai:spring-ai-starter-model-deepseek` ✅
- `org.springframework.boot:spring-boot-starter-webmvc` ✅（4.0.8）
- `org.springframework.boot:spring-boot-starter-test` ✅（4.0.8）
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1` ✅

### 3.2 Spring Boot 4 必须遵守的变更（否则直接踩坑）

1. **`spring-boot-starter-web` 已改名为 `spring-boot-starter-webmvc`**（Boot 4 模块化重构）。本项目统一使用新名字。
2. Boot 4 默认使用 **Jackson 3**（包名 `tools.jackson`）。本项目不直接引用 Jackson API，**无影响**。
3. Boot 4 拆分了测试模块：`spring-boot-starter-webmvc` 对应 `spring-boot-starter-webmvc-test`。本计划的测试策略刻意规避了这个包路径变更点（见 §4.4）。

---

## 4. 架构决策（Assumptions & Decisions）

### 4.1 传输协议：HTTP + SSE（按你的要求）

配置 `spring.ai.mcp.server.protocol=SSE`，使用同一套 starter `spring-ai-starter-mcp-server-webmvc`。

服务端端点（官方文档确认的默认值）：

| 端点 | 默认值 | 用途 |
|---|---|---|
| `sse-endpoint` | `/sse` | 客户端建立 SSE 长连接、获取后续 message 端点地址 |
| `sse-message-endpoint` | `/mcp/message` | 客户端 POST 发送 JSON-RPC 消息 |

> ⚠️ **必须知道的事实**：Spring AI 官方文档明确标注 `SSE WebMVC` **自 2.0.0 起已 deprecated**，推荐 `STREAMABLE`。保留 SSE 的原因是你需要兼容 Spring AI 1.0 时代的 MCP 客户端。**能跑，但属于"遗留兼容"路线**，讲解中会专门说明。

### 4.2 【关键决策】为什么拆成两个 Maven 模块 + 两个进程

你原本可以只做一个应用。但**单应用内让 MCP Client 通过 SSE 连自己（`localhost:8080`）存在真实的启动时序竞态**：

- `spring.ai.mcp.client.initialized` 默认为 `true`，即在 **Bean 创建阶段**就发起 `initialize()` 并拉取工具列表；
- 而 MVC 侧的 `WebMvcSseServerTransportProvider` 处理器映射在**同一阶段**才注册；
- Spring Boot 的 Servlet 容器虽在 `onRefresh()` 启动，但单例 Bean 的实例化顺序由依赖图决定 → **顺序不确定**，容易出现"启动时连不上自己"，甚至启动失败。

而且，「同进程自己连自己」本身**不是 MCP 的真实用法**——MCP 的价值就在跨进程/跨语言互通。

**因此采用 Maven 多模块 + 两个独立进程：**

| 模块 | 端口 | 角色 |
|---|---|---|
| `calculator-mcp-server` | 8080 | MCP Server（SSE）+ REST API + Swagger |
| `calculator-mcp-client` | 8081 | MCP Client（SSE → 8080）+ DeepSeek ChatClient + Swagger |

**收益**：完全走 Spring AI 官方自动配置（直接注入 `ToolCallbackProvider`），无手工生命周期代码、无竞态；同时真实演示了 MCP 的客户端/服务端分离架构。**代价**：父 pom 多约 15 行 XML，运行时要先起 server 再起 client。

### 4.3 其余决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 数值类型 | `double` | Demo 教学优先，避免 BigDecimal 噪音；除零显式判空 |
| 除零处理 | 抛 `DivisionByZeroException extends RuntimeException` | REST 侧 → HTTP 400；MCP 侧 Spring AI 文档确认 **RuntimeException 会作为 error result 回给模型**（模型可自行纠错），正好做教学点 |
| REST 风格 | `GET` + query 参数 | 浏览器/Swagger 直接可试，最省事 |
| 错误响应体 | Spring 内置 `ProblemDetail`（RFC 9457） | 零额外依赖，Boot 原生支持 |
| 服务端配置 | 只写必要项，**不写** `capabilities` | 规避属性名不匹配风险；默认全开，无副作用 |
| 客户端 LLM 配置 | **只配 `spring.ai.deepseek.api-key`** | Spring AI 2.0 重构了 options 配置键（移除了 `.options` 段），不写 model/temperature 可完全规避键名变更风险，用默认 `deepseek-chat` |
| API Key | 环境变量 `DEEPSEEK_API_KEY` | 仓库中零明文密钥 |
| 额外依赖 | 不引入 Validation / Lombok / Actuator / MapStruct | 保持最小可读 |
| 客户端测试 | 不写自动化测试 | 客户端启动**强依赖** `DEEPSEEK_API_KEY`（未设置即启动失败），不适合放进 `mvn test`。测试要求（四则运算）由服务端模块覆盖 |
| 讲解交付 | 对话中完整讲解 + `README.md` | 你明确要求"需要给出讲解"，且这是可复用的学习型 Demo，落文档是合理交付物 |

### 4.4 测试策略（规避 Boot 4 包路径变更）

Boot 4 模块化把 `@WebMvcTest` 等注解搬到了新的 test 模块（如 `spring-boot-webmvc-test`），包路径可能变化。为**彻底规避该风险**，测试采用：

- `CalculatorServiceTest`：纯 JUnit 5 单测，不加载 Spring 容器（最快、最稳）
- `CalculatorControllerTest`：`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`（这两个 API 从 Boot 3 起位置稳定，Boot 4 沿用）

---

## 5. 目标目录结构

```
d:\CodeRepo\RAG\brooks-mcp-demo\
├── .gitignore
├── .trae\documents\spring-boot4-spring-ai2-mcp-calculator-demo.md   ← 本计划
├── README.md                                                          ← 讲解文档
├── pom.xml                                                            ← 父 pom（packaging=pom）
├── calculator-mcp-server\                                             ← 端口 8080
│   ├── pom.xml
│   └── src\
│       ├── main\java\com\brooks\mcp\server\
│       │   ├── CalculatorMcpServerApplication.java
│       │   ├── service\CalculatorService.java
│       │   ├── exception\DivisionByZeroException.java
│       │   ├── mcp\CalculatorMcpTools.java                ← @McpTool 四则运算工具
│       │   ├── api\CalculatorController.java              ← REST
│       │   ├── api\GlobalExceptionHandler.java
│       │   ├── api\dto\ArithmeticResponse.java
│       │   └── config\OpenApiConfig.java
│       ├── main\resources\application.yml
│       └── test\java\com\brooks\mcp\server\
│           ├── service\CalculatorServiceTest.java
│           └── api\CalculatorControllerTest.java
└── calculator-mcp-client\                                             ← 端口 8081
    ├── pom.xml
    └── src\
        ├── main\java\com\brooks\mcp\client\
        │   ├── CalculatorMcpClientApplication.java
        │   ├── chat\CalculatorChatService.java
        │   ├── api\ChatController.java
        │   ├── api\dto\ChatRequest.java
        │   ├── api\dto\ChatResponse.java
        │   └── config\OpenApiConfig.java
        └── main\resources\application.yml
```

---

## 6. 分步实施（Proposed Changes）

### Step 0 — Git 初始化 + `.gitignore`

在 `d:\CodeRepo\RAG\brooks-mcp-demo` 执行 `git init`。

`.gitignore` 内容：

```gitignore
target/
*.log
.idea/
*.iml
.vscode/
.DS_Store
```

### Step 1 — 父 pom：`pom.xml`

- `parent` = `spring-boot-starter-parent:4.0.8`
- `groupId=com.brooks.mcp`，`artifactId=brooks-mcp-demo`，`version=1.0.0`，`packaging=pom`
- `modules` = 两个子模块
- `properties`：`java.version=25`、`maven.compiler.release=25`、`project.build.sourceEncoding=UTF-8`、`spring-ai.version=2.0.1`、`springdoc.version=3.1.1`
- `dependencyManagement`：`import` 引入 `spring-ai-bom:2.0.1`
- `pluginManagement`：声明 `spring-boot-maven-plugin`（版本由 parent 管理）

### Step 2 — 服务端模块 `calculator-mcp-server`

**`calculator-mcp-server/pom.xml`** 依赖：

| artifact | scope |
|---|---|
| `org.springframework.boot:spring-boot-starter-webmvc` | compile |
| `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` | compile |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdoc.version}` | compile |
| `org.springframework.boot:spring-boot-starter-test` | test |

（版本除 springdoc 外全部由父 pom 的 BOM / Boot parent 管理）

**Java 文件（`com.brooks.mcp.server` 包）**

1. `CalculatorMcpServerApplication` — 标准 `@SpringBootApplication` + `main`

2. `service/CalculatorService` — 纯业务逻辑，`@Service`，4 个方法：
   - `add(a,b) → a+b`
   - `subtract(a,b) → a-b`
   - `multiply(a,b) → a*b`
   - `divide(a,b)`：`b == 0` 时 `throw new DivisionByZeroException("除数不能为 0")`，否则 `a/b`

3. `exception/DivisionByZeroException extends RuntimeException`（构造器接收 message）

4. `mcp/CalculatorMcpTools` — `@Component`，构造器注入 `CalculatorService`，4 个方法，每个方法对应一个 MCP 工具：

```java
@McpTool(name = "add", description = "计算两个数之和，返回 a + b")
public double add(
        @McpToolParam(description = "第一个加数", required = true) double a,
        @McpToolParam(description = "第二个加数", required = true) double b) {
    return calculatorService.add(a, b);
}
```

`subtract` / `multiply` / `divide` 同构（描述分别为差 / 积 / 商，`divide` 的描述注明"除数为 0 时返回错误"）。

**导入包**：`org.springframework.ai.mcp.annotation.McpTool`、`org.springframework.ai.mcp.annotation.McpToolParam`。
> 若编译期这两个包名报错，**回退方案**：用 `mvn dependency:copy-dependencies` 或 IDE 在 `spring-ai-starter-mcp-server-webmvc` 的依赖树里定位 `McpTool.class` 实际包路径后修正 import。功能设计不受影响。

5. `api/dto/ArithmeticResponse` — Java `record`：

```java
@Schema(description = "四则运算结果")
public record ArithmeticResponse(
        @Schema(description = "运算类型", example = "add") String operation,
        @Schema(description = "第一个操作数", example = "10") double a,
        @Schema(description = "第二个操作数", example = "5") double b,
        @Schema(description = "运算结果", example = "15") double result) {
}
```

6. `api/CalculatorController` — `@RestController` + `@RequestMapping("/api/v1/calculator")` + `@Tag(name = "四则运算")`：

| 方法 | 路径 | 返回 |
|---|---|---|
| `add` | `GET /add?a=&b=` | `ArithmeticResponse("add", a, b, result)` |
| `subtract` | `GET /subtract?a=&b=` | `ArithmeticResponse("subtract", ...)` |
| `multiply` | `GET /multiply?a=&b=` | `ArithmeticResponse("multiply", ...)` |
| `divide` | `GET /divide?a=&b=` | `ArithmeticResponse("divide", ...)` |

每个方法用 `@Operation(summary = "...")` + `@Parameter(description = "...", example = "...")` 补全 Swagger 元数据。

7. `api/GlobalExceptionHandler` — `@RestControllerAdvice`，捕获 `DivisionByZeroException` → 返回 `ProblemDetail`：

```java
@ExceptionHandler(DivisionByZeroException.class)
public ProblemDetail handleDivisionByZero(DivisionByZeroException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problem.setTitle("非法运算");
    problem.setType(URI.create("https://example.com/problems/division-by-zero"));
    return problem;
}
```

8. `config/OpenApiConfig` — `@Configuration`，注册 `OpenAPI` bean（`Info.title="Calculator MCP Server API"`、`version="1.0.0"`、描述中说明"同时通过 /sse 暴露同名 MCP 工具"）

**`src/main/resources/application.yml`**

```yaml
server:
  port: 8080

spring:
  application:
    name: calculator-mcp-server
  ai:
    mcp:
      server:
        name: calculator-mcp-server
        version: 1.0.0
        protocol: SSE
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

**测试**

- `service/CalculatorServiceTest`（纯 JUnit 5，`new CalculatorService()`）：
  加法、减法、乘法、除法各 1 例；负数相加 1 例；`divide(1, 0)` 断言抛 `DivisionByZeroException`
- `api/CalculatorControllerTest`（`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`）：
  - `GET /api/v1/calculator/add?a=10&b=5` → 200，`result == 15`
  - `GET /api/v1/calculator/multiply?a=10&b=5` → 200，`result == 50`
  - `GET /api/v1/calculator/divide?a=1&b=0` → 400
  - `GET /v3/api-docs` → 200（确认 Swagger 文档已生成）

### Step 3 — 客户端模块 `calculator-mcp-client`

**`calculator-mcp-client/pom.xml`** 依赖：

| artifact | scope |
|---|---|
| `org.springframework.boot:spring-boot-starter-webmvc` | compile |
| `org.springframework.ai:spring-ai-starter-mcp-client` | compile |
| `org.springframework.ai:spring-ai-starter-model-deepseek` | compile |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdoc.version}` | compile |

> 用标准版 `spring-ai-starter-mcp-client`（SSE 基于 JDK HttpClient），**不引入 WebFlux**，避免不必要的响应式自动配置。

**Java 文件（`com.brooks.mcp.client` 包）**

1. `CalculatorMcpClientApplication` — 标准 `@SpringBootApplication` + `main`

2. `chat/CalculatorChatService` — `@Service`，注入 `ChatClient.Builder` 和 MCP 自动配置产出的 `ToolCallbackProvider`：

```java
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
```

> `.tools(ToolCallbackProvider)` 是 Spring AI 官方《Getting Started with MCP》文档给出的写法。
> **回退方案**：若该重载不存在，改用 `.toolCallbacks(mcpTools.getToolCallbacks())`。

3. `api/dto/ChatRequest` — `record ChatRequest(@Schema(...) String message)`
4. `api/dto/ChatResponse` — `record ChatResponse(String answer)`
5. `api/ChatController` — `@RestController` + `@RequestMapping("/api/v1/chat")` + `@Tag(name = "MCP 对话")`：
   - `POST /api/v1/chat`，body `{"message": "帮我算一下 (12 + 8) * 3 等于多少"}` → `{"answer": "..."}`
6. `config/OpenApiConfig` — 同服务端，标题改为 `Calculator MCP Client API`

**`src/main/resources/application.yml`**

```yaml
server:
  port: 8081

spring:
  application:
    name: calculator-mcp-client
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
    mcp:
      client:
        name: calculator-mcp-client
        version: 1.0.0
        type: SYNC
        sse:
          connections:
            calculator-server:
              url: http://localhost:8080
              sse-endpoint: /sse

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

### Step 4 — `README.md`（讲解文档）

包含：项目定位与架构图（文字版）、两个模块职责、启动步骤、REST/Swagger/MCP 三种调用方式、DeepSeek Key 配置方法、SSE 的 deprecated 说明与 STREAMABLE 迁移提示、以及 Boot 4 / Spring AI 2 的踩坑清单（starter 改名、Jackson 3、`@WebMvcTest` 包迁移、options 配置键变更）。

### Step 5 — 首次提交

`git add` 具体文件后提交一次，commit message 说明这是初始 Demo 骨架。

---

## 7. 验证步骤（Verification）

### 7.1 构建 + 测试

```powershell
mvn -q clean package
```

预期：两个模块 `BUILD SUCCESS`，`calculator-mcp-server` 的 6+ 个测试全部通过。

### 7.2 启动并验证服务端（8080）

```powershell
mvn -pl calculator-mcp-server spring-boot:run
```

| 检查项 | 命令 / 地址 | 预期 |
|---|---|---|
| 加法 | `curl "http://localhost:8080/api/v1/calculator/add?a=10&b=5"` | `{"operation":"add",...,"result":15.0}` |
| 除法 | `curl "http://localhost:8080/api/v1/calculator/divide?a=10&b=5"` | `result: 2.0` |
| 除零 | `curl "http://localhost:8080/api/v1/calculator/divide?a=1&b=0"` | HTTP **400** + `ProblemDetail`（含"除数不能为 0"） |
| Swagger UI | `http://localhost:8080/swagger-ui.html` | 页面打开，"四则运算"标签下有 4 个接口 |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` | 返回 JSON 文档 |
| MCP SSE 端点 | `curl -N http://localhost:8080/sse` | 先收到 `event: endpoint`，data 指向 `/mcp/message?...` |

### 7.3 启动并验证客户端（8081）

```powershell
$env:DEEPSEEK_API_KEY="你的key"
mvn -pl calculator-mcp-client spring-boot:run
```

启动日志中确认 MCP 客户端已连上并发现 **4 个工具**（tools/list）。

| 检查项 | 操作 | 预期 |
|---|---|---|
| Swagger UI | `http://localhost:8081/swagger-ui.html` | 打开，`MCP 对话` 标签存在 |
| LLM 调工具 | `POST /api/v1/chat` body `{"message":"帮我算一下 (12 + 8) * 3 等于多少"}` | 模型调用 `add`、`multiply` 工具后返回正确答案 **60** |

### 7.4 可选：用官方 MCP Inspector 独立验证（证明"跨客户端互通"）

```powershell
npx @modelcontextprotocol/inspector
```

Transport 选 **SSE**，URL 填 `http://localhost:8080/sse`，应能列出并直接调用 4 个工具。

### 7.5 提交前核对

- `git status` 中**没有任何**包含 API Key 的文件
- `.trae/` 是否纳入版本控制按你的偏好决定（默认纳入，因为计划文档本身有参考价值）

---

## 8. 已知风险与回退方案

| 风险 | 概率 | 回退方案 |
|---|---|---|
| `org.springframework.ai.mcp.annotation.McpTool` 包名与 2.0.1 实际不符 | 低 | 从依赖 jar 中用 IDE 反查实际包路径后改 import（§4.4 Step 2 已注明） |
| `.tools(ToolCallbackProvider)` 重载不存在 | 低 | 改用 `.toolCallbacks(mcpTools.getToolCallbacks())` |
| `protocol: SSE` 属性在该版本写法有差异 | 低 | 以 `application.yml` 启动日志 / `spring-boot-properties-migrator` 提示为准修正 |
| `spring.ai.deepseek` 属性前缀差异 | 低 | 已通过"只配 api-key"最小化暴露面；必要时查 `DeepSeekChatProperties` |
| 客户端启动时服务端未起 → 工具列表为空 | 中（操作顺序问题） | 运行手册明确"先 server 后 client" |

---

## 9. 交付物清单

1. Git 仓库（含 `.gitignore` + 首次提交）
2. Maven 多模块工程（父 pom + 2 个子模块）
3. 可运行的 MCP Server：`/sse` + `/mcp/message`，4 个工具
4. REST API `GET /api/v1/calculator/{add,subtract,multiply,divide}` + Swagger UI
5. MCP Client + DeepSeek 对话接口 `POST /api/v1/chat` + Swagger UI
6. 服务端单元测试 + Web 层集成测试
7. `README.md` 讲解文档
8. **对话中的完整讲解**（架构、MCP 原理、SSE vs STREAMABLE、Boot 4 / Spring AI 2 变更点、如何自己扩展）