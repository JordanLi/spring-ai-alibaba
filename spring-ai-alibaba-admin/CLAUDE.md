# Spring AI Alibaba Admin (Agent Studio)

## 项目定位
基于 Spring AI Alibaba 构建的 Agent 开发与评估平台（Agent Studio）。提供模型配置、Prompt 管理、测评集管理、实验评估、工作流及 Agent 编排、知识库与文档切片管理等一站式可视化与 API 能力。

## 核心架构
项目采用前后端分离架构：
- **前端**：Monorepo（Umi 4 + React），包含工作台 SPA（`packages/main`）、可视化工作流编辑（`packages/spark-flow`）及国际化工具链。
- **后端**：基于 Spring Boot 3.5.x + Spring AI 1.1.x 的模块化架构。
- **基础设施**：MySQL（关系数据）、Elasticsearch（文档切片/检索）、OSS（文件存储）及大模型提供商（DashScope / OpenAI / DeepSeek / Ollama 等）。

详情请参考：[架构示意图](docs/architecture.svg) | [外部依赖视图](docs/external-deps.svg)

## 关键模块
后端工程划分为四个核心模块（依赖关系见 [模块依赖图](docs/module-deps.svg)）：
- **`server-start`**：Spring Boot 可执行入口，负责最终装配。
- **`server-openapi`**：对外提供 REST API（如 `/api/v1/apps`）和控制台 HTTP 接口。
- **`server-core`**：核心领域模型与业务逻辑（评测域与 Studio 域数据）。
- **`server-runtime`**：运行期框架与公共通用工具。

## 关键约定
- **统一响应**：API 统一使用 `Result<T>` 封装（包含 `code`, `message`, `data`）。
- **分页规范**：列表返回常使用 `PagingList<T>` 或 `PageResult<T>`，查询参量含 `current` / `pageSize`。
- **接口鉴权**：`/console/v1/**` 面向控制台，依赖登录态；`/api/v1/apps/**` 为 OpenAPI，依赖 AK 等机制。
- **流式输出**：大模型对话、运行过程等请求通过 `stream=true` 或特定路径返回 SSE（`SseEmitter`）或 NDJSON（`Flux`）。
- **数据与接口详情**：
  - 接口与调用详见 [REST 接口清单](docs/api-list.md)。
  - 核心字段、表映射、主外键等详见 [数据模型说明](docs/data-model.md) 与 [数据模型 ER 图](docs/data-model-er.svg)。

## 怎么跑
项目要求 **JDK 17** 及 **Maven 3.6+**。
- **编译构建**：
  ```bash
  # 在父项目根目录，或者当前 admin 目录执行
  mvn clean package -DskipTests
  ```
- **本地启动**：
  进入 `server-start` 模块，配置好 MySQL 和 Elasticsearch 连接后，运行该模块的 Spring Boot 主类（如 `GeneratorApplication`）即可启动后端服务。

## 禁区
*(待补充)*

## 历史包袱
*(待补充)*