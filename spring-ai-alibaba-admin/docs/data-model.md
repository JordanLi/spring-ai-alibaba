# Spring AI Alibaba Admin — 核心数据模型

依据 `docker/middleware/init/mysql/admin-schema.sql`、`docker/middleware/init/mysql/agentscope-schema.sql`，以及 **`spring-ai-alibaba-admin-server-start`** 下 `com.alibaba.cloud.ai.studio.admin.entity.*DO`（评测 / Prompt / `model_config`）、**`spring-ai-alibaba-admin-server-core`** 下 `com.alibaba.cloud.ai.studio.core.base.entity.*Entity`（Studio）整理。生成时间：2026-05-11。

下表统一为 **「字段 | SQL 类型 | 说明」** 三列：说明列内用 **PK** / **FK** / **UK** / **枚举** 标注语义；类型以 DDL 为准。每表下另有 **键与约束摘要**（主键、DDL 外键、唯一键、枚举型字段一览）。**逻辑 FK** 表示业务引用但无 `CONSTRAINT`。

**与 `data-model-er.svg` 分工**：SVG 表达核心实体关系，并在每个模型卡片中列出字段/类型/主外键/枚举摘要；**完整逐字段说明以本文为准**。

## 1. 双库与代码映射

| 初始化脚本 | 典型用途 | 持久化代码（对齐实体） |
|------------|----------|--------------------------|
| `admin-schema.sql` | 评测集、实验、评估器、Prompt、全局模型连接配置 | `admin.entity.*DO` + `mapper/*.xml`；API 层 DTO 见 `com.alibaba.cloud.ai.studio.admin.dto` |
| `agentscope-schema.sql` | 账号、工作区、应用、知识库、插件/工具、Provider/Model、MCP、Agent Schema 等 | `core.base.entity.*Entity`（MyBatis-Plus `@TableName`） |

**说明**：文档 **分片（chunk）** 在业务中多写入 **Elasticsearch**，MySQL 中无 `document_chunk` 表；API 中的 `DocumentChunk` 为检索/编辑用领域对象，不对应本仓库 DDL 单表。

---

## 2. 评测域（`admin-schema.sql`）

### 2.1 `dataset`（测评集）

> **模型说明**：存放评测用数据集主数据（名称、列定义、逻辑删除），版本与行数据见子表。

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT(20) UNSIGNED NOT NULL AI | **PK**；自增主键 |
| name | VARCHAR(255) NOT NULL | 测评集名称 |
| description | TEXT | 描述 |
| columns_config | LONGTEXT | 列结构配置（JSON） |
| create_time | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME NOT NULL ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT(1) NOT NULL DEFAULT 0 | 逻辑删除：**枚举取值** `0` 未删、`1` 已删 |

**键与约束摘要**：**PK** `id`。**DDL 外键**：无。**唯一**：无。**枚举型字段**：无独立状态枚举；仅 `deleted` 为 0/1 标志。

---

### 2.2 `dataset_version`（测评集版本）

> **模型说明**：同一测评集下的发布版本，挂 DDL 外键到 `dataset`；含版本状态与条数统计。

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT(20) UNSIGNED NOT NULL AI | **PK** |
| **dataset_id** | BIGINT(20) UNSIGNED NOT NULL | **FK → dataset(id)** ON DELETE CASCADE |
| version | VARCHAR(32) NOT NULL | 版本号 |
| description | TEXT | 版本说明 |
| data_count | INT(11) NOT NULL DEFAULT 0 | 本版本数据条数 |
| status | VARCHAR(32) NOT NULL DEFAULT 'DRAFT' | **枚举** 见下 |
| experiments | TEXT | 关联实验集合（JSON） |
| dataset_items | TEXT | 数据项集合（JSON） |
| create_time | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME NOT NULL ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**键与约束摘要**：**PK** `id`。**DDL 外键** `dataset_id` → `dataset(id)` CASCADE。**唯一** `uk_dataset_version(dataset_id, version)`。**枚举** `status`：`DRAFT` \| `PUBLISHED` \| `ARCHIVED`（库注释）。

---

### 2.3 `dataset_item`（数据行）

> **模型说明**：测评集下的单行数据（JSON 内容），通过 `dataset_id` 归属父测评集（无指向 `dataset_version` 的 DDL FK）。

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT(20) UNSIGNED NOT NULL AI | **PK** |
| **dataset_id** | BIGINT(20) UNSIGNED NOT NULL | **FK → dataset(id)** ON DELETE CASCADE |
| columns_config | LONGTEXT | 列结构（JSON） |
| data_content | LONGTEXT NOT NULL | 行数据（JSON） |
| create_time | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME NOT NULL ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT(1) NOT NULL DEFAULT 0 | 逻辑删除：**枚举取值** `0` / `1` |

**键与约束摘要**：**PK** `id`。**DDL 外键** `dataset_id` → `dataset(id)` CASCADE。**唯一**：无。**枚举**：无业务状态枚举；仅 `deleted` 0/1。

---

### 2.4 `evaluator`（评估器）

> **模型说明**：评估器主档（名称与描述），具体打分逻辑在 `evaluator_version`。

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT(20) UNSIGNED NOT NULL AI | **PK** |
| name | VARCHAR(255) NOT NULL | 评估器名称 |
| description | TEXT | 描述 |
| create_time | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME NOT NULL ON UPDATE CURRENT_TIMESTAMP | 更新时间 |
| deleted | TINYINT(1) NOT NULL DEFAULT 0 | 逻辑删除：**枚举取值** `0` / `1` |

**键与约束摘要**：**PK** `id`。**DDL 外键**：无。**唯一**：无。**枚举**：仅 `deleted` 0/1。

---

### 2.5 `evaluator_version`（评估器版本）

> **模型说明**：评估器某一版本的模型配置、Prompt 与变量，供实验引用。

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT(20) UNSIGNED NOT NULL AI | **PK** |
| **evaluator_id** | BIGINT(20) UNSIGNED NOT NULL | **FK → evaluator(id)** ON DELETE CASCADE |
| description | TEXT | 版本说明 |
| version | VARCHAR(32) NOT NULL | 版本号 |
| model_config | TEXT NOT NULL | 模型配置 |
| prompt | LONGTEXT | Prompt 配置（JSON） |
| variables | LONGTEXT | Prompt 变量参数 |
| status | VARCHAR(32) NULL | **枚举** 见下 |
| experiments | TEXT | 实验集合（JSON） |
| create_time | DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME NOT NULL ON UPDATE CURRENT_TIMESTAMP | 更新时间 |

**键与约束摘要**：**PK** `id`。**DDL 外键** `evaluator_id` → `evaluator(id)` CASCADE。**唯一** `uk_evaluator_version(evaluator_id, version)`。**枚举** `status`：`DRAFT` \| `PUBLISHED` \| `ARCHIVED`（库注释，可空）。

---

### 2.6 `evaluator_template`（评估模板库）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| evaluator_template_key | VARCHAR(255) UK | 模板业务键 |
| template_desc | VARCHAR(255) | 描述 |
| template / variables / model_config | LONGTEXT | 模板正文、变量、推荐模型参数 |

---

### 2.7 `experiment`（实验）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| name / description | VARCHAR / TEXT | 实验名与描述 |
| **dataset_id** | BIGINT UNSIGNED NOT NULL | **逻辑关联 dataset**（无 DDL 级 FK） |
| **dataset_version_id** | BIGINT UNSIGNED NOT NULL | **逻辑关联 dataset_version** |
| dataset_version | VARCHAR(32) NOT NULL | 版本号冗余 |
| evaluation_object_config | LONGTEXT | 评测对象 JSON |
| evaluator_config | TEXT NOT NULL | 评估器配置 |
| status | VARCHAR(32) DEFAULT 'DRAFT' | 实验状态 |
| progress | INT DEFAULT 0 | 进度 0–100 |
| complete_time | DATETIME | 完成时间 |
| create_time / update_time | DATETIME | |

**枚举 `status`（库注释）**：`DRAFT` | `RUNNING` | `COMPLETED` | `FAILED` | `STOPPED`

---

### 2.8 `experiment_result`（实验明细结果）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **experiment_id** | BIGINT UNSIGNED NOT NULL | **逻辑关联 experiment**（索引，无 FK） |
| input / actual_output / reference_output | LONGTEXT | 输入、实际输出、参考输出 |
| score | DECIMAL(3,2) | 分数 0.0–1.0 |
| reason | TEXT | 评分理由 |
| evaluation_time | DATETIME | 评估时间 |
| **evaluator_version_id** | BIGINT UNSIGNED NOT NULL | **逻辑关联 evaluator_version** |
| create_time / update_time | DATETIME | |

---

### 2.9 `prompt`（Prompt 主表）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| prompt_key | VARCHAR(255) UK | 业务键 |
| prompt_desc | VARCHAR(255) | 描述 |
| latest_version | VARCHAR(32) | 最新版本号展示 |
| tags | VARCHAR(255) | 标签 |
| create_time / update_time | DATETIME(3) | |

**实体类**：`PromptDO`（JPA `@Table(name="prompt")`）。

---

### 2.10 `prompt_version`（Prompt 版本）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| prompt_key | VARCHAR(255) NOT NULL | **逻辑关联 prompt.prompt_key**（非 FK） |
| version | VARCHAR(32) NOT NULL | 版本号 |
| version_desc | VARCHAR(255) | 版本说明 |
| template / variables / model_config | LONGTEXT | 模板、变量、调试模型参数 JSON |
| status | VARCHAR(32) DEFAULT 'pre' | 版本状态 |
| previous_version | VARCHAR(32) | 上一版本 |
| create_time | DATETIME(3) | |

**唯一约束**：`(prompt_key, version)`  

**枚举 `status`（库注释）**：`pre`（预发布）| `release`（正式）

---

### 2.11 `prompt_build_template`（Prompt 构建模板）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| prompt_template_key | VARCHAR(255) UK | 模板键 |
| tags / template_desc | VARCHAR | 标签与描述 |
| template / variables / model_config | LONGTEXT | 模板与推荐参数 |

**实体类**：`PromptTemplateDO`。

---

### 2.12 `model_config`（全局模型连接配置）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT AI | **PK** |
| name | VARCHAR(100) UK | 配置显示名 |
| provider | VARCHAR(50) NOT NULL | 提供方标识 |
| model_name | VARCHAR(100) NOT NULL | 模型标识 |
| base_url | VARCHAR(500) NOT NULL | 服务地址 |
| api_key | VARCHAR(500) NOT NULL | 密钥 |
| default_parameters / supported_parameters | JSON | 默认与可配置参数 |
| status | TINYINT DEFAULT 1 | 启用/禁用 |
| create_time / update_time | DATETIME | |
| deleted | TINYINT(1) | 逻辑删除 |

**枚举 `status`**：`1` 启用 | `0` 禁用  

**实体类**：`ModelConfigDO`（JPA）。

---

## 3. Studio 域（`agentscope-schema.sql` + `*Entity`）

以下与 DDL 一致；**实体类** 均在 `com.alibaba.cloud.ai.studio.core.base.entity` 包（`@TableName` 与表名对应）。

### 3.1 `account`（账号）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **account_id** | VARCHAR(64) NOT NULL | 业务账号 ID，**UK** `uk_account_id` |
| username | VARCHAR(255) NOT NULL | 登录名 |
| email / mobile | VARCHAR(255) | 联系方式 |
| password | VARCHAR(255) NOT NULL | 密码（哈希存储） |
| nickname / icon | VARCHAR(255) | 展示信息 |
| **type** | VARCHAR(64) NOT NULL | **枚举（库注释）**：`basic` \| `admin` |
| **status** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 已删除、`1` 正常 |
| gmt_create / gmt_modified | DATETIME NOT NULL | 创建/修改时间 |
| gmt_last_login | DATETIME | 最近登录 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`AccountEntity`。

---

### 3.2 `workspace`（工作区）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **workspace_id** | VARCHAR(64) NOT NULL | 业务工作区 ID，**UK** |
| **account_id** | VARCHAR(64) NOT NULL | **逻辑 FK → account.account_id**（`idx_account_id`） |
| **status** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 删除、`1` 正常 |
| name | VARCHAR(255) NOT NULL | 名称 |
| description | VARCHAR(4096) | 描述 |
| config | TEXT | JSON 扩展配置 |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`WorkspaceEntity`。

---

### 3.3 `application`（应用）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **workspace_id** | VARCHAR(64) NOT NULL | **逻辑 FK → workspace.workspace_id** |
| **app_id** | VARCHAR(64) NOT NULL | 业务应用 ID，**UK** |
| name | VARCHAR(255) NOT NULL | 应用名 |
| description | VARCHAR(4096) | 描述 |
| icon | VARCHAR(255) | 图标 |
| source | VARCHAR(64) NOT NULL | 来源标识 |
| **type** | VARCHAR(64) NOT NULL | **枚举（库注释）**：`agent` \| `workflow` 等 |
| **status** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` deleted，`1` draft，`2` published，`3` published_editing |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`AppEntity`。

---

### 3.4 `application_version`（应用版本）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **app_id** | VARCHAR(64) NOT NULL | **逻辑 FK → application.app_id** |
| **workspace_id** | VARCHAR(64) NOT NULL | 冗余工作区 |
| config | LONGTEXT | 工作流/Agent 配置 JSON |
| **status** | TINYINT NOT NULL | 同 `application.status` 语义 |
| version | VARCHAR(32) NOT NULL DEFAULT '0.0.1' | 版本号 |
| description | VARCHAR(4096) | 版本说明 |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`AppVersionEntity`。

---

### 3.5 `application_component`（应用组件）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| code | VARCHAR(64) NOT NULL | 组件业务编码 |
| name | VARCHAR(128) NOT NULL | 名称 |
| **workspace_id** | VARCHAR(64) NOT NULL | **逻辑 FK → workspace** |
| **type** | VARCHAR(64) NOT NULL | **枚举（库注释）**：`agent` \| `workflow` |
| **app_id** | VARCHAR(64) NULL | 可选绑定 **application.app_id** |
| config | LONGTEXT | 组件配置 JSON |
| description | VARCHAR(4096) | 描述 |
| **status** | TINYINT NULL | **枚举（库注释）**：`0` 删除、`1` 正常、`2` 已发布 |
| need_update | TINYINT NULL | `0` 无需更新 / `1` 需更新 |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NULL | 审计 |

**实体**：`AppComponentEntity`。

---

### 3.6 `api_key`（API 密钥）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **account_id** | VARCHAR(64) NOT NULL | **逻辑 FK → account.account_id** |
| **api_key** | VARCHAR(512) NOT NULL | 密钥串，**UK** |
| **status** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 删除、`1` 正常 |
| description | VARCHAR(4096) | 说明 |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`ApiKeyEntity`。

---

### 3.7 `plugin`（插件）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **plugin_id** | VARCHAR(64) NOT NULL | 业务插件 ID，**UK** |
| **workspace_id** | VARCHAR(64) NOT NULL | **逻辑 FK → workspace** |
| **type** | VARCHAR(64) NOT NULL | **枚举（库注释）**：`1` 官方、`2` 自定义（以实际写入为准） |
| **status** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 删除、`1` 正常 |
| name | VARCHAR(255) NOT NULL | 插件名 |
| description | VARCHAR(4096) | 描述 |
| config | TEXT | 配置 JSON |
| source | VARCHAR(64) NOT NULL | 来源 |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`PluginEntity`。

---

### 3.8 `tool`（工具）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **plugin_id** | VARCHAR(64) NOT NULL | **逻辑 FK → plugin.plugin_id**（`idx_workspace_plugin`） |
| **tool_id** | VARCHAR(64) NOT NULL | 业务工具 ID，**UK** |
| **workspace_id** | VARCHAR(64) NOT NULL | 工作区 |
| **status** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 删除、`1` 正常 |
| **enabled** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 禁用、`1` 启用 |
| **test_status** | TINYINT NOT NULL DEFAULT 1 | **枚举（库注释）**：`1` 未测、`2` 通过、`3` 失败 |
| name | VARCHAR(255) NOT NULL | 工具名 |
| description | VARCHAR(4096) | 描述 |
| config | LONGTEXT NOT NULL | 工具配置 |
| api_schema | LONGTEXT NOT NULL | OpenAPI/工具 Schema |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`ToolEntity`。

---

### 3.9 `knowledge_base`（知识库）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **workspace_id** | VARCHAR(64) NOT NULL | **逻辑 FK → workspace** |
| **kb_id** | VARCHAR(64) NOT NULL | 业务知识库 ID，**UK** |
| **type** | VARCHAR(64) NOT NULL | 如 `unstructured`（库注释） |
| **status** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 删除、`1` 正常 |
| name | VARCHAR(255) NOT NULL | 名称 |
| description | VARCHAR(4096) | 描述 |
| process_config / index_config / search_config | TEXT | 处理/索引/检索配置 JSON |
| total_docs | BIGINT UNSIGNED NOT NULL DEFAULT 0 | 文档计数 |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`KnowledgeBaseEntity`。

---

### 3.10 `document`（知识库文档）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **workspace_id** | VARCHAR(64) NOT NULL | 工作区 |
| **kb_id** | VARCHAR(64) NOT NULL | **逻辑 FK → knowledge_base.kb_id** |
| **doc_id** | VARCHAR(64) NOT NULL | 业务文档 ID，**UK** |
| **type** | VARCHAR(64) NOT NULL | **枚举（库注释）**：`file` \| `url` |
| **status** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 删除、`1` 正常 |
| **enabled** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 禁用、`1` 启用 |
| name | VARCHAR(255) NOT NULL | 文档名 |
| format | VARCHAR(64) NOT NULL | 格式 |
| size | BIGINT NOT NULL DEFAULT 0 | 大小 |
| metadata | TEXT | 元数据 JSON |
| **index_status** | TINYINT NOT NULL DEFAULT 1 | **枚举（库注释）**：`1` 待处理、`2` 处理中、`3` 完成（与 Java `DocumentIndexStatus` 可存在数值扩展，以代码为准） |
| path | VARCHAR(512) NOT NULL | 存储路径 |
| parsed_path | VARCHAR(512) | 解析产物路径 |
| process_config | TEXT | 分片/处理配置 |
| source | VARCHAR(255) | 来源 |
| error | TEXT | 错误信息 |
| gmt_create / gmt_modified | TIMESTAMP NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`DocumentEntity`。分片正文多在 **Elasticsearch**，见前文说明。

---

### 3.11 `reference`（通用引用）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| main_code | VARCHAR(64) NOT NULL | 主实体编码 |
| **main_type** | TINYINT NOT NULL | 主实体类型（多态，**无 FK**） |
| refer_code | VARCHAR(64) NOT NULL | 被引用实体编码 |
| **refer_type** | TINYINT NOT NULL | 被引用类型（多态） |
| workspace_id | VARCHAR(64) NOT NULL DEFAULT '1' | 工作区隔离 |

**实体**：`ReferEntity`。

---

### 3.12 `mcp_server`（MCP 服务）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| server_code | VARCHAR(64) NOT NULL | 服务编码（`idx_code`） |
| name | VARCHAR(64) NOT NULL | 名称 |
| description | VARCHAR(1024) | 描述 |
| source | VARCHAR(128) | 来源 |
| deploy_env | VARCHAR(16) | 部署环境：`local` / `remote`（库注释） |
| **type** | VARCHAR(32) NOT NULL | **枚举（库注释）**：`OFFICIAL` \| `CUSTOMER` |
| deploy_config | TEXT NOT NULL | 部署配置 JSON |
| workspace_id / account_id | VARCHAR(64) NULL | 归属工作区 / 账号（**逻辑 FK**，无 DDL FK） |
| **status** | TINYINT NOT NULL | **枚举（库注释）**：`0` 不可用、`1` 正常、`3` 删除 |
| biz_type | VARCHAR(512) | 业务类型 |
| detail_config | TEXT | 详情配置 |
| host | VARCHAR(1024) | 访问地址 |
| **install_type** | VARCHAR(32) NULL | **枚举（库注释）**：`npx` / `uvx` / `sse` |

**实体**：`McpServerEntity`。

---

### 3.13 `provider`（模型供应商）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT AI | **PK** |
| workspace_id | VARCHAR(64) NULL | **逻辑 FK → workspace** |
| icon / name / description | VARCHAR | 展示与描述 |
| **provider** | VARCHAR(255) NOT NULL | 供应商标识（与 workspace 组合查询） |
| **enable** | TINYINT(1) DEFAULT 1 | **枚举**：`0` 禁用、`1` 启用 |
| **source** | VARCHAR(64) NOT NULL DEFAULT 'preset' | **枚举（库注释）**：`preset` \| `custom` |
| credential | VARCHAR(1024) | 凭证 JSON（可能加密） |
| supported_model_types | VARCHAR(255) | 支持的模型类型列表 |
| protocol | VARCHAR(64) | 如 `openai` |
| gmt_create / gmt_modified | DATETIME | 时间戳 |
| creator / modifier | VARCHAR(64) | 审计 |

**实体**：`ProviderEntity`。与 `model` 无 DDL FK，靠 `workspace_id` + `provider` 关联。

---

### 3.14 `model`（工作区模型条目）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT AI | **PK** |
| workspace_id | VARCHAR(64) NULL | 工作区 |
| icon | VARCHAR(255) | 图标 |
| name | VARCHAR(100) NULL | 展示名 |
| **type** | VARCHAR(100) DEFAULT 'LLM' | 模型类型，如 `LLM`、`rerank`（见种子数据） |
| **mode** | VARCHAR(100) DEFAULT 'chat' | 模式，如 `chat` |
| **model_id** | VARCHAR(100) NOT NULL | 模型 ID（与 provider 组合） |
| **provider** | VARCHAR(100) NOT NULL | **逻辑关联 provider.provider** |
| **enable** | TINYINT(1) DEFAULT 1 | **枚举**：`0` / `1` |
| tags | VARCHAR(255) | 标签 |
| **source** | VARCHAR(100) NOT NULL DEFAULT 'preset' | `preset` \| `custom` |
| gmt_create / gmt_modified | DATETIME | 时间戳 |
| creator / modifier | VARCHAR(64) | 审计 |

**实体**：`ModelEntity`。

---

### 3.15 `agent_schema`（Agent 编排模式）

| 字段 | SQL 类型 | 说明 |
|------|----------|------|
| **id** | BIGINT UNSIGNED AI | **PK** |
| **agent_id** | VARCHAR(64) NULL | 业务 Agent ID，**UK**（可空时视业务写入） |
| **workspace_id** | VARCHAR(64) NOT NULL | **逻辑 FK → workspace** |
| name | VARCHAR(255) NOT NULL | 名称 |
| description | VARCHAR(4096) | 描述 |
| **type** | VARCHAR(64) NOT NULL | **枚举（库注释）**：`ReactAgent`、`ParallelAgent`、`SequentialAgent`、`LLMRoutingAgent`、`LoopAgent` 等 |
| instruction | TEXT | 系统指令 |
| input_keys | TEXT | 入参键 JSON |
| output_key | VARCHAR(255) | 出参键 |
| handle | LONGTEXT | Handle 配置 JSON |
| sub_agents | LONGTEXT | 子 Agent 配置 JSON |
| yaml_schema | LONGTEXT | 生成的 YAML Schema |
| **status** | VARCHAR(64) NOT NULL DEFAULT 'DRAFT' | **枚举（库注释）**：`DRAFT` \| `PUBLISHED` \| `ARCHIVED` |
| **enabled** | TINYINT NOT NULL DEFAULT 1 | **枚举**：`0` 禁用、`1` 启用 |
| gmt_create / gmt_modified | DATETIME NOT NULL | 时间戳 |
| creator / modifier | VARCHAR(64) NOT NULL | 审计 |

**实体**：`AgentSchemaEntity`。

---

**说明**：`server-core` 另有 `LimitEntity`（`LimitEntity.java`），**本仓库 `agentscope-schema.sql` 未包含对应建表**；若环境中有独立迁移脚本，需另行对齐。

---

## 4. DTO 与表的关系（摘要）

| 区域 | 代表 DTO / 领域对象 | 对应持久化 |
|------|---------------------|------------|
| 评测 API | `Dataset`、`DatasetVersion`、`DatasetItem`、`Experiment`、`Evaluator`…（`admin.dto`） | `dataset`、`dataset_version`、`dataset_item`、`experiment*`、`evaluator*`；行映射见 `admin.entity` 包内 `DatasetDO`、`ExperimentDO` 等 |
| Prompt API | `Prompt`、`PromptVersion`、`PromptTemplate`… | `prompt`、`prompt_version`、`prompt_build_template` |
| 模型配置 API | `ModelConfigResponse` | `model_config` |
| Studio API | `Application`、`Workspace`、`Document`、`Tool`…（`runtime.domain` 等） | `application*`、`workspace`、`document`、`tool`… |

DTO 字段常比表多 **聚合字段**（列表统计、嵌套子对象），以 OpenAPI/JSON 序列化结果为准；**与表同名的标量字段**一般可与上文章节逐列对照。

### API 中存在但无对应单表的实体声明

在 [REST 接口清单 (api-list.md)](./api-list.md) 中部分核心实体/对象在 MySQL 中并无单独建表，具体机制如下：

- **`ChatSession`（调试会话）**：Prompt 调试等接口生成的会话状态，采用 `ConcurrentHashMap` 内存存储（生产环境推荐对接 Redis），无对应 MySQL 表。
- **`GlobalConfig`（全局配置）**：控制台返回的全局系统级配置参数，来自环境变量与代码内置类定义，无单独 MySQL 表。
- **`TraceSpanDTO` / `OverviewStatsDTO`（可观测性）**：链路追踪与统计数据依赖底层的 OpenTelemetry、Elasticsearch 等外部系统，不在关系型数据库 DDL 范围内。
- **`UploadPolicy`（上传策略）**：单纯的数据传输与 OSS 签名授权下发凭证对象，无落库。
- **各类 `Response` / `Event`（如 `TaskRunResponse`、`RunEvent`）**：纯运行时过程交互对象及 SSE 事件流体，执行状态存入任务引擎中，而非业务表。
- **`reference`（引用关系表）**：在数据模型中存在此表（`ReferEntity`），但 API 层面无直接对外的 `ReferenceController` 增删改查，而是隐含在应用组件引用等内部业务逻辑中（如 `query-refer` 接口）。

---

## 5. ER 图文件

关键实体之间的 **主外键（及强逻辑关联）** 见同目录 **`data-model-er.svg`**（双泳道：评测库 / Studio 库 + 注记）。

- **实线箭头**：MySQL DDL 中的 `FOREIGN KEY`（admin 库：`dataset`→`dataset_version`→无直连 `dataset_item`；`dataset`→`dataset_item`；`evaluator`→`evaluator_version` 等）。
- **虚线箭头**：无 DDL FK 的业务引用（如 `experiment.dataset_id`、`experiment_result.evaluator_version_id`）。
- **修正**：`dataset_item` 仅 **FK 到 `dataset`**；图中 **实线** 为「中轴」`dataset`→`dataset_version`→`dataset_item`，**`dataset_item`→`dataset`** 用 **折线**：两端在两表 **左框中点**，中间经 **列外竖段**（如 x 略小于左框），避免与同列中轴、指向 `experiment` 的虚线混成一条。
