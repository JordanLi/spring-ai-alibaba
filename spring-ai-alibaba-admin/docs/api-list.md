# Spring AI Alibaba Admin — REST 接口清单

本文档根据 `spring-ai-alibaba-admin-server-start` 与 `spring-ai-alibaba-admin-server-openapi` 模块中的 `@RestController` 源码整理，**按业务模块分组**。生成时间：2026-05-11。

**收录范围（仅 REST / HTTP）**：仅整理由 Spring Web 暴露的 **`@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` / `@PatchMapping`**（及类上的 `@RequestMapping`）所形成的路径；含 `ApplicationController` / `DSLController` / `RunnerController` 所实现的接口 **`default` 方法上的同类映射**（仍由同一 Spring MVC 调度，本质为 HTTP）。

**不包含**：Feign 客户端、Dubbo/gRPC/RSocket 等 RPC 服务接口、消息监听器（如 `@KafkaListener`）、以及未映射到 HTTP 的内部 Service 方法。本仓库 `spring-ai-alibaba-admin` 下 Java 源码中亦未发现 `@FeignClient`、Dubbo、gRPC 等声明。

## 约定

- **统一响应**：多数控制台与部分 `/api` 接口使用 `com.alibaba.cloud.ai.studio.runtime.domain.Result<T>`（含 `code`、`message`、`data` 等字段，具体以类型为准）。评测域 DTO 另见 `com.alibaba.cloud.ai.studio.admin.dto` 包。
- **分页**：常见为 `PagingList<T>`、`PageResult<T>`，查询参数多为 `current`、`pageSize` 或各 Request 内分页字段。
- **鉴权**：`/console/v1/**` 通常需登录态；`/api/v1/apps/**` 为对外 OpenAPI，需按网关/应用配置鉴权。
- **流式**：`stream=true` 或 `produces = TEXT_EVENT_STREAM` / NDJSON 的接口返回 **SSE** 或 **Flux**，非 JSON 单对象。

---

## 1. 对外 OpenAPI（`server-openapi`）

**基础路径**：`/api/v1/apps`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/api/v1/apps/chat/completions` | Agent 对话补全（支持流式） | Body: `AgentRequest`（含 `stream`、消息等） | `stream=true`：`SseEmitter`；否则为 **JSON 字符串**（`AgentResponse` 序列化）或错误 JSON |
| POST | `/api/v1/apps/workflow/completions` | 工作流同步/流式补全 | Body: `WorkflowRequest` | 同上：`SseEmitter` 或 **JSON 字符串**（`WorkflowResponse`） |
| POST | `/api/v1/apps/workflow/async-completions` | 工作流异步提交 | Body: `WorkflowRequest` | `Result<TaskRunResponse>` |
| POST | `/api/v1/apps/workflow/stop-completions` | 停止工作流任务 | Body: `TaskStopRequest`（`taskId`） | `Result<Boolean>` |
| POST | `/api/v1/apps/workflow/async-results` | 查询异步工作流执行结果 | Body: `AsyncResultRequest`（`taskId`） | `Result<AsyncResultResponse>` |

---

## 2. Prompt 管理（`/api`）

**控制器**：`PromptController`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/api/prompt` | 创建 Prompt | Body: `PromptCreateRequest` | `Result<Prompt>` |
| GET | `/api/prompt` | Prompt 详情 | Query: `promptKey` | `Result<Prompt>` |
| GET | `/api/prompts` | Prompt 分页列表 | Query: `PromptListRequest` | `Result<PageResult<Prompt>>` |
| PUT | `/api/prompt` | 更新 Prompt | Body: `PromptUpdateRequest` | `Result<Prompt>` |
| DELETE | `/api/prompt` | 删除 Prompt | Query: `promptKey` | `Result<Boolean>` |
| POST | `/api/prompt/version` | 创建 Prompt 版本 | Body: `PromptVersionCreateRequest` | `Result<PromptVersion>` |
| GET | `/api/prompt/version` | Prompt 版本详情 | Query: `promptKey`, `version` | `Result<PromptVersionDetail>` |
| GET | `/api/prompt/versions` | Prompt 版本分页列表 | Query: `PromptVersionListRequest` | `Result<PageResult<PromptVersion>>` |
| GET | `/api/prompt/template` | 模板详情 | Query: `promptTemplateKey` | `Result<PromptTemplateDetail>` |
| GET | `/api/prompt/templates` | 模板分页列表 | Query: `PromptTemplateListRequest` | `Result<PageResult<PromptTemplate>>` |
| POST | `/api/prompt/run` | Prompt 调试运行（NDJSON 流） | Body: `PromptRunRequest` | `Flux<PromptRunResponse>`（`application/x-ndjson`） |
| GET | `/api/prompt/session` | 查询调试会话 | Query: `sessionId` | `Result<ChatSession>` |
| DELETE | `/api/prompt/session` | 删除调试会话 | Query: `sessionId` | `Result<Void>` |

---

## 3. 模型配置（`/api`）

**控制器**：`ModelConfigController`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| GET | `/api/model/supported` | 支持的模型提供商列表 | 无 | `Result<List<String>>` |
| GET | `/api/models` | 模型配置分页列表 | Query: `ModelConfigQueryRequest` | `Result<PageResult<ModelConfigResponse>>` |
| GET | `/api/model` | 按 ID 查询模型配置 | Query: `id` (Long) | `Result<ModelConfigResponse>` |
| GET | `/api/models/enabled` | 已启用的模型配置列表 | 无 | `Result<List<ModelConfigResponse>>` |

---

## 4. 实验（`/api`）

**控制器**：`ExperimentController`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/api/experiment` | 创建实验 | Body: `ExperimentCreateRequest` | `Result<Experiment>` |
| GET | `/api/experiments` | 实验分页列表 | Query: `ExperimentListRequest` | `Result<PageResult<Experiment>>` |
| GET | `/api/experiment` | 实验详情 | Query: `experimentId` | `Result<Experiment>` |
| GET | `/api/experiment/results` | 实验评估结果概览列表 | Query: `experimentId` | `Result<List<ExperimentEvaluatorResult>>` |
| GET | `/api/experiment/result` | 实验评估结果明细分页 | Query: `ExperimentEvaluatorResultDetailListRequest` | `Result<PageResult<ExperimentEvaluatorResultDetail>>` |
| PUT | `/api/experiment/stop` | 停止实验 | Query: `experimentId` | `Result<Experiment>` |
| DELETE | `/api/experiment` | 删除实验 | Query: `experimentId` | `Result<Void>` |
| PUT | `/api/experiment/restart` | 重启实验 | Query: `experimentId` | `Result<Void>` |

---

## 5. 评估器（`/api/evaluator`）

**控制器**：`EvaluatorController`  
**注意**：类上路径为 `/api/evaluator`，方法上仍带 `/evaluator` 等子路径，完整 URL 形如 `/api/evaluator/evaluator`。

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/api/evaluator/evaluator` | 创建评估器 | Body: `EvaluatorCreateRequest` | `Result<Evaluator>` |
| POST | `/api/evaluator/evaluatorVersion` | 创建评估器版本 | Body: `EvaluatorVersionCreateRequest` | `Result<EvaluatorVersion>` |
| GET | `/api/evaluator/evaluators` | 评估器分页列表 | Query: `EvaluatorListRequest` | `Result<PageResult<Evaluator>>` |
| GET | `/api/evaluator/evaluator` | 评估器详情 | Query: `id` (Long) | `Result<Evaluator>` |
| GET | `/api/evaluator/evaluatorVersions` | 评估器版本分页列表 | Query: `EvaluatorVersionListRequest` | `Result<PageResult<EvaluatorVersion>>` |
| PUT | `/api/evaluator/evaluator` | 更新评估器 | Body: `EvaluatorUpdateRequest` | `Result<Evaluator>` |
| DELETE | `/api/evaluator/evaluator` | 删除评估器 | Query: `id` | `Result<Void>` |
| POST | `/api/evaluator/debug` | 调试评估器 | Body: `EvaluatorTestRequest` | `Result<EvaluatorDebugResult>` |
| GET | `/api/evaluator/templates` | 评估模板分页列表 | Query: `EvaluatorTemplateListRequest` | `Result<PageResult<EvaluatorTemplate>>` |
| GET | `/api/evaluator/template` | 评估模板详情 | Query: `templateId` | `Result<EvaluatorTemplate>` |
| GET | `/api/evaluator/experiments` | 评估器关联实验分页 | Query: `EvaluatorExperimentsListRequest` | `Result<PageResult<Experiment>>` |

---

## 6. 数据集（`/api/dataset`）

**控制器**：`DatasetController`  
**注意**：`GET /api/dataset/dataItem` 方法标注为 `@PathVariable Long id`，但映射路径无 `{id}`，若运行时报错需以实际 Spring 映射或后续修复为准；下表按 **Query `id`** 理解意图。

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/api/dataset/dataset` | 创建测评集 | Body: `DatasetCreateRequest` | `Result<Dataset>` |
| POST | `/api/dataset/datasetVersion` | 创建测评集版本 | Body: `DatasetVersionCreateRequest` | `Result<DatasetVersion>` |
| GET | `/api/dataset/datasets` | 测评集分页列表 | Query: `DatasetListRequest` | `Result<PageResult<Dataset>>` |
| GET | `/api/dataset/dataset` | 测评集详情 | Query: `datasetId` | `Result<Dataset>` |
| PUT | `/api/dataset/dataset` | 更新测评集 | Body: `DatasetUpdateRequest` | `Result<Dataset>` |
| DELETE | `/api/dataset/dataset` | 删除测评集 | Query: `datasetId` | `Result<Void>` |
| POST | `/api/dataset/dataItem` | 批量创建数据项 | Body: `DatasetItemCreateRequest` | `Result<List<DatasetItem>>` |
| GET | `/api/dataset/dataItems` | 数据项分页列表 | Query: `DatasetItemListRequest` | `Result<PageResult<DatasetItem>>` |
| GET | `/api/dataset/dataItem` | 数据项详情 | Query: `id`（实现与注解可能不一致） | `Result<DatasetItem>` |
| PUT | `/api/dataset/dataItem` | 更新数据项 | Body: `DatasetItemUpdateRequest` | `Result<DatasetItem>` |
| DELETE | `/api/dataset/dataItem` | 删除数据项 | Query: `id` | `Result<Void>` |
| GET | `/api/dataset/datasetVersions` | 测评集版本分页列表 | Query: `DatasetVersionListRequest` | `Result<PageResult<DatasetVersion>>` |
| PUT | `/api/dataset/datasetVersion` | 更新测评集版本 | Body: `DatasetVersionUpdateRequest` | `Result<DatasetVersion>` |
| GET | `/api/dataset/experiments` | 数据集关联实验分页 | Query: `DatasetExperimentsListRequest` | `Result<PageResult<Experiment>>` |
| POST | `/api/dataset/dataItemFromTrace` | 从 Trace 创建数据项 | Body: `DataItemCreateFromTraceRequest` | `Result<List<DatasetItem>>` |

---

## 7. 可观测性 / Trace（`/api/observability`）

**控制器**：`ObservabilityController`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| GET | `/api/observability/traces` | Trace 分页列表 | Query: `TracesQueryRequest` | `Result<PageResult<TraceSpanDTO>>` |
| GET | `/api/observability/traces/{traceId}` | Trace 详情 | Path: `traceId` | `Result<TraceDetailDTO>` |
| GET | `/api/observability/services` | 服务列表 | Query: `ServicesQueryRequest` | `Result<ServicesResponseDTO>` |
| GET | `/api/observability/overview` | 概览统计 | Query: `OverviewQueryRequest` | `Result<OverviewStatsDTO>` |

---

## 8. 控制台 — 认证与账号

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/auth/login` | 登录 | Body: `LoginRequest` | `Result<TokenResponse>` |
| POST | `/console/v1/auth/refresh-token` | 刷新令牌 | Body: `RefreshTokenRequest` | `Result<TokenResponse>` |
| POST | `/console/v1/auth/logout` | 登出 | Header/Cookie 等随请求 | `Result<Void>` |
| POST | `/console/v1/accounts` | 创建账号 | Body: `Account` | `Result<String>` |
| PUT | `/console/v1/accounts/{accountId}` | 更新账号 | Path + Body: `Account` | `Result<String>` |
| DELETE | `/console/v1/accounts/{accountId}` | 删除账号 | Path: `accountId` | `Result<Void>` |
| GET | `/console/v1/accounts/{accountId}` | 账号详情 | Path: `accountId` | `Result<Account>` |
| GET | `/console/v1/accounts` | 账号分页列表 | Query: `BaseQuery` | `Result<PagingList<Account>>` |
| PUT | `/console/v1/accounts/change-password` | 修改密码 | Body: `ChangePasswordRequest` | `Result<String>` |
| GET | `/console/v1/accounts/profile` | 当前用户资料 | 无 | `Result<Account>` |

**OAuth2**（`Oauth2Controller`，`/oauth2`）

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| GET | `/oauth2/login/github` | 跳转 GitHub 登录 | 无 | `Result<String>`（多为授权 URL） |
| GET | `/oauth2/callback/github` | GitHub OAuth 回调 | Query: `code` | `void`（重定向写响应） |

---

## 9. 控制台 — 系统

**`SystemController`**：`/console/v1/system`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| GET | `/console/v1/system/global-config` | 全局配置（登录方式、上传方式等） | 无 | `Result<GlobalConfig>` |
| GET | `/console/v1/system/health` | 健康检查 | 无 | **纯文本** `String`（非 `Result`） |

---

## 10. 控制台 — 工作区

**`WorkspaceController`**：`/console/v1/workspaces`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/workspaces` | 创建工作区 | Body: `Workspace` | `Result<String>` |
| PUT | `/console/v1/workspaces/{workspaceId}` | 更新工作区 | Path + Body | `Result<String>` |
| DELETE | `/console/v1/workspaces/{workspaceId}` | 删除工作区 | Path: `workspaceId` | `Result<Void>` |
| GET | `/console/v1/workspaces/{workspaceId}` | 工作区详情 | Path: `workspaceId` | `Result<Workspace>` |
| GET | `/console/v1/workspaces` | 工作区分页列表 | Query: `BaseQuery` | `Result<PagingList<Workspace>>` |

---

## 11. 控制台 — 应用（App）

**`AppController`**：`/console/v1/apps`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/apps` | 创建应用 | Body: `Application` | `Result<String>` |
| PUT | `/console/v1/apps/{appId}` | 更新应用 | Path + Body: `Application` | `Result<String>` |
| DELETE | `/console/v1/apps/{appId}` | 删除应用 | Path: `appId` | `Result<Void>` |
| GET | `/console/v1/apps/{appId}` | 应用详情 | Path: `appId` | `Result<Application>` |
| GET | `/console/v1/apps` | 应用分页列表 | Query: `AppQuery` | `Result<PagingList<Application>>` |
| POST | `/console/v1/apps/{appId}/publish` | 发布应用 | Path: `appId` | `Result<Void>` |
| GET | `/console/v1/apps/{appId}/versions` | 版本分页列表 | Path + Query | `Result<PagingList<ApplicationVersion>>` |
| GET | `/console/v1/apps/{appId}/versions/{version}` | 指定版本详情 | Path | `Result<ApplicationVersion>` |
| POST | `/console/v1/apps/{appId}/copy` | 复制应用 | Path: `appId` | `Result<String>` |

---

## 12. 控制台 — 工作流调试与 SSE

**`WorkflowController`**：类映射 `/console/v1/apps`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/apps/workflow/debug/run-task` | 调试执行工作流任务 | Body: `TaskRunRequest` | `Result<TaskRunResponse>` |
| POST | `/console/v1/apps/workflow/debug/get-task-process` | 查询调试任务进度 | Body: `ProcessGetRequest` | `Result<ProcessGetResponse>` |
| POST | `/console/v1/apps/workflow/debug/init` | 初始化调试入参列表 | Body: `InitRequest` | `Result<List<TaskRunParam>>` |
| POST | `/console/v1/apps/workflow/debug/resume-task` | 恢复暂停任务 | Body: `TaskResumeRequest` | `Result<TaskResumeResponse>` |
| POST | `/console/v1/apps/workflow/debug/part-graph/run-task` | 子图调试执行 | Body: `TaskPartGraphRequest` | `Result<TaskPartGraphResponse>` |
| POST | `/console/v1/apps/workflow/debug/part-graph/stop-task` | 停止子图任务 | Body: `TaskStopRequest` | `Result<Boolean>` |
| POST | `/console/v1/apps/workflow/{appId}/run_stream` | 工作流 SSE 流式执行 | Path: `appId`；Body: `ApiTaskRunRequest` | `SseEmitter`（`text/event-stream`） |

---

## 13. 控制台 — Agent 对话（草稿）

**`AppChatController`**：`/console/v1/apps`（`draft=true` 由服务端设置）

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/apps/chat/completions` | 控制台 Agent 对话（支持 SSE） | Body: `AgentRequest` | `SseEmitter` 或 `AgentResponse`（同步） |

---

## 14. 控制台 — 模型与提供商

**`ModelController`**：`/console/v1/models`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| GET | `/console/v1/models/{modelType}/selector` | 按类型拉取模型选择器数据 | Path: `modelType` | `Result<List<ModelProviderGroup>>` |
| GET | `/console/v1/models/enabled` | 已启用模型列表 | 无 | `Result<List<Map<String, Object>>>` |

**`ProviderController`**：`/console/v1/providers`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/providers` | 新增提供商 | Body: `AddProviderRequest` | `Result<Boolean>` |
| PUT | `/console/v1/providers/{provider}` | 更新提供商 | Path + Body: `UpdateProviderRequest` | `Result<Boolean>` |
| DELETE | `/console/v1/providers/{provider}` | 删除提供商 | Path: `provider` | `Result<Boolean>` |
| GET | `/console/v1/providers` | 提供商列表 | Query: `QueryProviderRequest` | `Result<List<ProviderConfigInfo>>` |
| GET | `/console/v1/providers/{provider}` | 提供商详情 | Path: `provider` | `Result<ProviderConfigInfo>` |
| POST | `/console/v1/providers/{provider}/models` | 新增模型 | Path + Body: `AddModelRequest` | `Result<Boolean>` |
| PUT | `/console/v1/providers/{provider}/models/{modelId}` | 更新模型 | Path + Body: `UpdateModelRequest` | `Result<Boolean>` |
| DELETE | `/console/v1/providers/{provider}/models/{modelId}` | 删除模型 | Path | `Result<Boolean>` |
| GET | `/console/v1/providers/{provider}/models` | 模型列表 | Path: `provider` | `Result<List<ModelConfigInfo>>` |
| GET | `/console/v1/providers/{provider}/models/{modelId}` | 模型详情 | Path | `Result<ModelConfigInfo>` |
| GET | `/console/v1/providers/{provider}/models/{modelId}/parameter_rules` | 模型参数规则 | Path | `Result<List<ParameterRule>>` |
| GET | `/console/v1/providers/protocols` | 支持的协议列表 | 无 | `Result<List<String>>` |

---

## 15. 控制台 — 知识库、文档、分片

**`KnowledgeBaseController`**：`/console/v1/knowledge-bases`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/knowledge-bases` | 创建知识库 | Body: `KnowledgeBase` | `Result<String>` |
| PUT | `/console/v1/knowledge-bases/{kbId}` | 更新知识库 | Path + Body | `Result<String>` |
| DELETE | `/console/v1/knowledge-bases/{kbId}` | 删除知识库 | Path: `kbId` | `Result<Void>` |
| GET | `/console/v1/knowledge-bases/{kbId}` | 知识库详情 | Path | `Result<KnowledgeBase>` |
| GET | `/console/v1/knowledge-bases` | 分页列表 | Query: `BaseQuery` | `Result<PagingList<KnowledgeBase>>` |
| POST | `/console/v1/knowledge-bases/query-by-codes` | 按编码批量查询 | Body: `KnowledgeBaseQuery` | `Result<List<KnowledgeBase>>` |
| POST | `/console/v1/knowledge-bases/retrieve` | 检索分片 | Body: `DocumentRetrieverQuery` | `Result<List<DocumentChunk>>` |

**`DocumentController`**：`/console/v1/knowledge-bases`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/knowledge-bases/{kbId}/documents` | 创建文档 | Path + Body: `CreateDocumentRequest` | `Result<List<String>>`（文档 ID） |
| PUT | `/console/v1/knowledge-bases/{kbId}/documents/{docId}` | 更新文档 | Path + Body: `Document` | `Result<Void>` |
| DELETE | `/console/v1/knowledge-bases/{kbId}/documents/{docId}` | 删除文档 | Path | `Result<Void>` |
| DELETE | `/console/v1/knowledge-bases/{kbId}/documents/batch-delete` | 批量删文档 | Path + Body: `DeleteDocumentRequest` | `Result<Void>` |
| GET | `/console/v1/knowledge-bases/{kbId}/documents/{docId}` | 文档详情 | Path | `Result<Document>` |
| GET | `/console/v1/knowledge-bases/{kbId}/documents` | 文档分页列表 | Path + Query: `DocumentQuery` | `Result<PagingList<Document>>` |
| PUT | `/console/v1/knowledge-bases/{kbId}/documents/{docId}/re-index` | 重新索引 | Path + Body: `IndexDocumentRequest` | `Result<Void>` |

**`DocumentChunkController`**：`/console/v1/documents`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/documents/{docId}/chunks` | 创建分片 | Path + Body: `DocumentChunk` | `Result<String>` |
| PUT | `/console/v1/documents/{docId}/chunks/{chunkId}` | 更新分片 | Path + Body | `Result<Void>` |
| DELETE | `/console/v1/documents/{docId}/chunks/{chunkId}` | 删除分片 | Path | `Result<Void>` |
| DELETE | `/console/v1/documents/{docId}/chunks/batch-delete` | 批量删分片 | Path + Body: `DeleteChunkRequest` | `Result<Void>` |
| GET | `/console/v1/documents/{docId}/chunks` | 分片分页列表 | Path + Query: `BaseQuery` | `Result<PagingList<DocumentChunk>>` |
| POST | `/console/v1/documents/{docId}/chunks/preview` | 分片预览 | Path + Body: `IndexDocumentRequest` | `Result<List<DocumentChunk>>` |
| PUT | `/console/v1/documents/{docId}/chunks/update-status` | 批量更新分片启用状态 | Path + Body: `UpdateChunkRequest` | `Result<Void>` |

---

## 16. 控制台 — 文件

**`FileController`**：`/console/v1/files`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/files/upload` | 上传文件 | Multipart: `files` | `Result<List<UploadPolicy>>` |
| GET | `/console/v1/files/download` | 下载文件 | Query: `path` | `void`（流式写响应） |
| POST | `/console/v1/files/upload-policies` | 获取上传策略 | Body: `WebUploadRequest` | `Result<List<WebUploadPolicy>>` |
| GET | `/console/v1/files/get-preview-url` | 预览 URL | Query: `path` | `Result<String>` |

---

## 17. 控制台 — 工具、插件、MCP、API Key、Agent Schema、组件

**`ToolController`**：`/console/v1/tools`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/tools` | 创建工具 | Body: `ToolEntity` | `Result<ToolEntity>` |
| PUT | `/console/v1/tools/{id}` | 更新工具 | Path + Body | `Result<ToolEntity>` |
| DELETE | `/console/v1/tools/{id}` | 删除工具 | Path: `id` | `Result<Void>` |
| GET | `/console/v1/tools/{id}` | 工具详情 | Path | `Result<ToolEntity>` |
| GET | `/console/v1/tools` | 全部工具列表 | 无 | `Result<List<ToolEntity>>` |
| GET | `/console/v1/tools/page` | 分页 | Query: `current`, `pageSize` 等 | `Result<PagingList<ToolEntity>>` |
| GET | `/console/v1/tools/search` | 按名称搜索 | Query: `name` | `Result<List<ToolEntity>>` |
| GET | `/console/v1/tools/plugin/{pluginId}` | 按插件列工具 | Path | `Result<List<ToolEntity>>` |
| PATCH | `/console/v1/tools/{id}/enabled` | 启用/禁用 | Path + Query: `enabled` | `Result<Void>` |

**`PluginController`**：`/console/v1`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/plugins` | 创建插件 | Body: `Plugin` | `Result<String>` |
| PUT | `/console/v1/plugins/{pluginId}` | 更新插件 | Path + Body | `Result<Void>` |
| DELETE | `/console/v1/plugins/{pluginId}` | 删除插件 | Path | `Result<Void>` |
| GET | `/console/v1/plugins/{pluginId}` | 插件详情 | Path | `Result<Plugin>` |
| GET | `/console/v1/plugins` | 插件分页列表 | Query: `BaseQuery` | `Result<PagingList<Plugin>>` |
| POST | `/console/v1/plugins/{pluginId}/tools` | 在插件下创建工具 | Path + Body: `Tool` | `Result<String>` |
| PUT | `/console/v1/plugins/{pluginId}/tools/{toolId}` | 更新工具 | Path + Body | `Result<String>` |
| DELETE | `/console/v1/plugins/{pluginId}/tools/{toolId}` | 删除工具 | Path | `Result<Void>` |
| GET | `/console/v1/plugins/{pluginId}/tools/{toolId}` | 工具详情 | Path | `Result<Tool>` |
| GET | `/console/v1/plugins/{pluginId}/tools` | 工具分页列表 | Path + Query | `Result<PagingList<Tool>>` |
| POST | `/console/v1/tools/{toolId}/enable` | 启用工具 | Path | `Result<Void>` |
| POST | `/console/v1/tools/{toolId}/disable` | 禁用工具 | Path | `Result<Void>` |
| POST | `/console/v1/plugins/{pluginId}/tools/{toolId}/test` | 调试执行工具 | Path + Body: `ToolExecutionRequest` | `Result<ToolExecutionResult>` |
| POST | `/console/v1/plugins/{pluginId}/tools/{toolId}/publish` | 发布工具 | Path | `Result<Void>` |
| POST | `/console/v1/tools/query-by-ids` | 按 ID 批量查工具 | Body: `ToolQuery` | `Result<List<Tool>>` |

**`McpServerController`**：`/console/v1/mcp-servers`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/mcp-servers` | 创建 MCP 服务 | Body: `McpServerDetail` | `Result<String>` |
| PUT | `/console/v1/mcp-servers` | 更新 MCP 服务 | Body: `McpServerDetail` | `Result<String>` |
| DELETE | `/console/v1/mcp-servers/{serverCode}` | 删除 | Path | `Result<Void>` |
| GET | `/console/v1/mcp-servers/{serverCode}` | 详情 | Path + Query（可选 workspace） | `Result<McpServerDetail>` |
| GET | `/console/v1/mcp-servers` | 分页列表 | Query: `McpQuery` | `Result<PagingList<McpServerDetail>>` |
| POST | `/console/v1/mcp-servers/query-by-codes` | 按编码批量查 | Body: `McpQuery` | `Result<List<McpServerDetail>>` |
| POST | `/console/v1/mcp-servers/debug-tools` | 调试 MCP 工具调用 | Body: `McpServerCallToolRequest` | `Result<McpServerCallToolResponse>` |

**`ApiKeyController`**：`/console/v1/api-keys`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/api-keys` | 创建 API Key | Body: `ApiKey` | `Result<String>` |
| PUT | `/console/v1/api-keys/{id}` | 更新 | Path + Body | `Result<String>` |
| DELETE | `/console/v1/api-keys/{id}` | 删除 | Path | `Result<Void>` |
| GET | `/console/v1/api-keys/{id}` | 详情 | Path | `Result<ApiKey>` |
| GET | `/console/v1/api-keys` | 分页列表 | Query: `BaseQuery` | `Result<PagingList<ApiKey>>` |

**`AgentSchemaController`**：`/console/v1/agent-schemas`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/agent-schemas` | 创建 | Body: `AgentSchemaEntity` | `Result<AgentSchemaEntity>` |
| PUT | `/console/v1/agent-schemas/{id}` | 更新 | Path + Body | `Result<AgentSchemaEntity>` |
| DELETE | `/console/v1/agent-schemas/{id}` | 删除 | Path | `Result<Void>` |
| GET | `/console/v1/agent-schemas/{id}` | 详情 | Path | `Result<AgentSchemaEntity>` |
| GET | `/console/v1/agent-schemas` | 列表 | 无 | `Result<List<AgentSchemaEntity>>` |
| GET | `/console/v1/agent-schemas/page` | 分页 | Query: `current`, `pageSize` | `Result<PagingList<AgentSchemaEntity>>` |
| GET | `/console/v1/agent-schemas/search` | 搜索 | Query: `name` | `Result<List<AgentSchemaEntity>>` |
| PATCH | `/console/v1/agent-schemas/{id}/enabled` | 启用/禁用 | Path + Query: `enabled` | `Result<Void>` |

**`AppComponentController`**：`/console/v1/component-servers`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| GET | `/console/v1/component-servers` | 组件分页列表 | Query: `AppComponentQuery` | `Result<PagingList<AppComponent>>` |
| GET | `/console/v1/component-servers/app-publishable` | 可发布应用分页 | Query: `AppComponentQuery` | `Result<PagingList<Application>>` |
| POST | `/console/v1/component-servers` | 发布/注册组件 | Body: `AppComponentQuery` | `Result<String>` |
| PUT | `/console/v1/component-servers/{code}` | 更新组件 | Path + Body | `Result<String>` |
| DELETE | `/console/v1/component-servers/{code}` | 删除组件 | Path | `Result<Boolean>` |
| GET | `/console/v1/component-servers/{code}/detail-by-code` | 按 code 详情 | Path | `Result<AppComponent>` |
| GET | `/console/v1/component-servers/{appId}/detail-by-appid` | 按 appId 详情 | Path | `Result<AppComponent>` |
| GET | `/console/v1/component-servers/{code}/query-refer` | 引用关系 | Path | `Result<List<AppComponent>>` |
| GET | `/console/v1/component-servers/{appId}/query-config` | 应用配置 | Path | `Result<AppComponent>` |
| POST | `/console/v1/component-servers/query-by-codes` | 按 codes 批量查 | Body: `AppComponentQuery` | `Result<List<AppComponent>>` |
| GET | `/console/v1/component-servers/{code}/query-schema` | 单个 schema | Path | `Result<Map<String,Object>>` |
| POST | `/console/v1/component-servers/schema-by-codes` | 批量 schema | Body: `AppComponentQuery` | `Result<Map<String,Object>>` |

---

## 18. Graph Studio — 应用 / DSL / 运行器

实现类分别映射 **`graph-studio/api/app`**、**`graph-studio/api/dsl`**、**`graph-studio/api/run`**（无前导 `/` 时仍相对当前 Servlet context，一般为根路径拼接）。

**应用 `ApplicationController` + `AppAPI`**

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `graph-studio/api/app` | 创建应用 | Body: `CreateAppParam` | `R<App>` |
| GET | `graph-studio/api/app` | 应用列表 | 无 | `R<List<App>>` |
| GET | `graph-studio/api/app/{id}` | 应用详情 | Path: `id` | `R<App>` |
| PUT | `graph-studio/api/app` | 同步应用定义 | Body: `App` | `R<App>` |
| DELETE | `graph-studio/api/app/{id}` | 删除应用 | Path: `id` | `R<Boolean>` |

**DSL `DSLController` + `DSLAPI`**

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| GET | `graph-studio/api/dsl/export/{id}` | 导出 DSL 文本 | Path: `id`；Query: `dialect` | `R<String>` |
| GET | `graph-studio/api/dsl/export-file/{id}` | 导出 DSL 文件流 | Path；Query: `dialect` | `ResponseEntity<Resource>`（附件下载） |
| POST | `graph-studio/api/dsl/import` | 从文本导入 | Body: `DSLParam` | `R<App>` |
| POST | `graph-studio/api/dsl/import-file` | 从上传文件导入 | Multipart: `file`；Query: `dialect` | `R<App>` |

**Runner `RunnerController` + `RunnerAPI`**

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `graph-studio/api/run/app/{id}/stream` | 流式运行应用 | Path: `id`；Body: `Map<String,Object>` 输入 | `Flux<RunEvent>` |
| POST | `graph-studio/api/run/app/{id}/sync` | 同步运行应用 | Path；Body: 同上 | `R<RunEvent>` |

> `R<T>` 为生成器模块内 `com.alibaba.cloud.ai.studio.admin.builder.generator.common.R`。

---

## 19. 测试示例（非生产）

**`ApiExampleController`**：`/test/api/example`

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| GET | `/test/api/example/getOrder` | 示例订单 GET | Header/Query 透传 | `Map<String,Object>` |
| POST | `/test/api/example/getOrder` | 示例订单 POST | Body: `Map`（需 `orderId`） | `Map<String,Object>` |
| POST | `/test/api/example/getOrder/{orderId}` | 示例路径 + Body | Path；Body: `Map` | `Map<String,Object>` |

---

## 20. 独立启动类 `GeneratorApplication` 内 Mock（默认不随主应用启动）

**`MockLoginController`**（仅在使用 `GeneratorApplication` 作为 `main` 时生效）：提供固定假数据的登录与配置，路径与部分控制台接口相同，便于无中间件联调 DSL 页面。

| 方法 | 路径 | 说明 | 主要入参 | 返回结构 |
|------|------|------|----------|----------|
| POST | `/console/v1/auth/login` | Mock 登录 | Body: `LoginRequest` | `Result<TokenResponse>`（固定 token） |
| POST | `/console/v1/auth/refresh-token` | Mock 刷新 | Body: `RefreshTokenRequest` | `Result<TokenResponse>` |
| GET | `/console/v1/system/global-config` | Mock 全局配置 | 无 | `Result<GlobalConfig>` |
| GET | `/console/v1/accounts/profile` | Mock 当前用户 | 无 | `Result<Account>` |

---

## 附录：未列入的 Spring Web 组件

- `GlobalResponseBodyAdvice`、`GlobalExceptionHandler`、`RestExceptionHandler` 等为 **`@ControllerAdvice` / `@RestControllerAdvice`**，不直接映射 HTTP 路径。
- **`server-core`** 模块中未发现 `@RestController`。

如需与 OpenAPI/Swagger UI 对照，可在依赖已启用时访问 SpringDoc 暴露的文档端点（具体 `springdoc` 路径以 `application.yml` 为准）。
