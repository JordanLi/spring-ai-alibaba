---
name: docs-auto-sync
description: >-
  只读模式审计 spring-ai-alibaba-admin 仓库内代码（Controller / Entity·DO /
  Mapper XML / DDL schema sql）与文档（docs/api-list.md、docs/data-model.md）
  之间的漂移，输出一份结构化的 drift 报告。绝不写、不改、不删任何文件。触发场景：
  用户说"检查文档是否过期 / 文档跟代码对不上 / 同步文档 / docs drift / sync
  docs / verify api docs / verify data model docs"，或在合并 controller /
  mapper / entity / DDL 改动之后，或者作为 PR 提交前的 pre-flight 检查。报告
  分为五类问题：代码里有但 api-list.md 没记录的接口、api-list.md 里记录但代码
  里找不到对应方法的接口、*-schema.sql 里有但 data-model.md 没记录的表、字段
  类型/可空性/语义在 Entity、DDL、文档三方对不齐的列、Entity 字段与 DDL 列对
  不上的映射；每条 finding 给出文件 + 行号 + 一段可直接粘贴的 patch 片段，
  但是否落盘由人类决定，skill 仅打印报告后停止。
allowed-tools: [Read, Glob, Grep]
---

# 文档漂移巡检（只读）

## 这个 skill 做什么

把 spring-ai-alibaba-admin 的**活代码**和**两份给人看的文档**做一次完整比对，
输出一份结构化 drift 报告：

- `docs/api-list.md` ←→ `*Controller.java`
- `docs/data-model.md` ←→ `*DO.java` / `*Entity.java` / `docker/middleware/init/mysql/*-schema.sql`

输出**只有一份 markdown 报告**，并且报告末尾会显式打印 `Files modified: 0`
那一行，让用户清楚知道：什么都没改。

## 这个 skill 不做什么（硬契约，违反就是 bug）

- ❌ 不调 `Write`、`StrReplace`、`Shell`。`allowed-tools` 在工具层已经禁掉。
- ❌ 不提议 `git commit`、不建议跑 `mvn` / `make`、不串联下游 skill
  （`scaffold-resource` 等可写 skill）。
- ❌ 即使 finding 一行就能修好，也**不**自动修。
- ✅ 允许：在报告里给出可粘贴的 patch 片段，必须用代码围栏并标注
  `suggested patch (paste manually)`，让用户明白这只是文本建议。

如果用户看完报告说"那你修一下"，**当前 skill 立刻结束**，切换到有写权限的
skill 或者直接编辑。不要悄悄扩大本 skill 的职责边界。

## 输入参数

一轮问完；如果用户没给任何参数，按默认值跑。

| 参数 | 必填? | 默认 | 说明 |
|------|-------|------|------|
| `mode` | 否 | `sample` | `sample` / `full`。`sample`：每个 controller 子目录最多读 5 个 + DDL 全量；适合日常巡检（~13 个文件）。`full`：读所有 controller + 所有 Entity + 所有 DDL；只在 PR pre-flight 或周巡检时用（~50+ 个文件） |
| `scope` | 否 | `both` | `api` / `data` / `both`，指定审计范围 |
| `since` | 否 | （全量扫描） | 形如 `HEAD~10` 的 git 引用或日期；仅作为筛选提示打印在报告头部，本 skill 自己不会调 git |
| `domain` | 否 | `both` | `admin` / `studio` / `both`，按持久化约定过滤 |
| `severity_floor` | 否 | `info` | `info` / `warn` / `error`，低于此级别的项不出现在报告里 |

可选参数不要追问。把实际使用的默认值打印在报告头部即可。**`mode` 直接影响结论可信度**——`sample` 模式下，凡是"代码有文档无"和"文档有代码无"两类 finding 都**只能给出下界**，必须在覆盖范围声明里写清楚未读哪些文件。

## 真值源 & 比对目标

**真值源**（代码，权威）：

| 类别 | 路径 | 对应 api-list.md 节 |
|------|------|---------------------|
| Admin 域 REST 接口 | `spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/controller/*Controller.java` | §2–§7 |
| Studio 域 REST 接口 | `spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/builder/controller/*Controller.java` | §8–§17 |
| **OpenAPI 模块 REST 接口** | `spring-ai-alibaba-admin-server-openapi/src/main/java/**/controller/*Controller.java` | §1（`/api/v1/apps/**`） |
| **Generator / Graph Studio 接口** | `spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/builder/generator/controller/*Controller.java` 及 `**/graph-studio/api/**` | §18（`R<T>` 响应）、§20（`MockLoginController`） |
| Admin 域实体 | `spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/entity/*DO.java` | data-model.md §2 |
| Studio 域实体 | `spring-ai-alibaba-admin-server-core/src/main/java/com/alibaba/cloud/ai/studio/core/base/entity/*Entity.java` | data-model.md §3 |
| Admin 域 DDL | `docker/middleware/init/mysql/admin-schema.sql` | data-model.md §2 |
| Studio 域 DDL | `docker/middleware/init/mysql/agentscope-schema.sql` | data-model.md §3 |

> **glob 落空就报告**：扫描每一行路径时，若 Glob 结果为 0 文件（例如该模块尚未引入），不要静默跳过——在覆盖范围里显式标 `(0 files found, source path may have moved)`，让用户知道是 skill 找不到还是真的没有。

**比对目标**（文档）：

- `docs/api-list.md` — 按资源分组的接口列表
- `docs/data-model.md` — 按域分组的表清单（§2 评测/admin，§3 Studio，
  §4.1 API 中存在但无对应单表的实体声明）

## 执行步骤

严格按顺序跑。每一步执行完打印一行简短计数，方便用户看进度。

| # | 步骤 | 工具 | 具体动作 |
|---|------|------|----------|
| 1 | 确认输入 | (chat) | 打印一次解析后的 `mode` / `scope` / `domain` / `since` / `severity_floor`。可选项不要追问。同时打印将要扫描的"真值源"路径表（按 mode 折算后的实际文件数），让用户在大量读文件之前能 ctrl+c 中止。 |
| 2 | 提取代码侧接口 | `Glob` + `Read` | 用 Glob 把"真值源"表里 4 个 controller 路径全部展开。`mode=sample` 时：每个子目录按文件名字典序取**前 5 个** `*Controller.java`；`mode=full` 时：全部读。对每个文件提取：类级 `@RequestMapping` 前缀、方法级 `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping/@RequestMapping` 的路径、HTTP 动词、方法名、返回类型。组织成 `(verb, full_path, controller_class, method_name)`。**记录已读/未读的文件名清单，留给 Step 10 的 Coverage 节用**。 |
| 3 | 提取文档侧接口 | `Read` + `Grep` | 读 `docs/api-list.md`，解析每一行接口：markdown 表格里以 `\| <verb> \| <path>` 起的行，以及 `### <verb> <path>` 形式的子标题。组织成 `(verb, full_path, section_title, line_no)`。 |
| 4 | 接口 diff | (内存计算) | 产出三个列表：(a) **MISSING_IN_DOC**——代码有、文档无；(b) **MISSING_IN_CODE**——文档有、代码无（死文档）；(c) **MISMATCH**——`(verb, path)` 相同但签名冲突。第 (c) 类**只**检测易判定项：同 path 不同动词、path variable 名字不一致、请求体类名被重命名。不要尝试深度对比请求体字段——噪声太大。 |
| 5 | 提取代码侧表结构 | `Glob` + `Read` | 读两个 `*-schema.sql`，解析每个 `CREATE TABLE` 块为 `(table_name, [(column, sql_type, nullable, default, comment)])`；读所有 `*DO.java` / `*Entity.java`，从 Lombok / JPA / MyBatis-Plus 注解解析字段为 `(java_field, java_type, column_name_if_annotated)`。 |
| 6 | 提取文档侧表结构 | `Read` | 解析 `docs/data-model.md`。识别 `### x.y <table_name>（<cn name>）` 子标题及紧随其后的 `字段 \| SQL 类型 \| 说明` 表格行。组织成 `(table_name, [(column, sql_type, description)])`。 |
| 7 | 数据模型 diff | (内存计算) | 产出四个列表：(a) **TABLE_MISSING_IN_DOC**——DDL 有、文档无；(b) **TABLE_MISSING_IN_DDL**——文档有、DDL 无（先交叉 §4.1 "无对应单表的实体声明"清单——里面的条目本来就没表，不算 bug，只标记真正孤儿的）；(c) **FIELD_MISSING_IN_DOC**——列在 DDL 有、文档表里没；(d) **FIELD_TYPE_MISMATCH**——双方都有同名列但 `sql_type` 文本不同（大小写不敏感，且 `BIGINT(20)` 与 `BIGINT` 视为相同）。 |
| 8 | Entity ↔ DDL 交叉对照 | (内存计算) | 对每张表，把 DDL 列与实体字段对齐。标出：DDL 有但实体没映射的列；实体上 `@Column`/`@TableField` 指向 DDL 中不存在的列。跳过约定记账列（`id`、`gmt_create`、`gmt_modified`、`create_time`、`update_time`、`workspace_id`、`deleted`、`status`），除非它们自身定义不一致。 |
| 9 | 装配报告 | (内存计算) | 按下面"报告格式"渲染，最后再应用 `severity_floor` 过滤。 |
| 10 | 打印报告 | (chat) | 原样输出。结尾必须有 `Files modified: 0` 一行 + "下一步交给人类"菜单。 |
| 11 | 停止 | (chat) | 不要提议 `git commit`，不要串联其他 skill。等用户决定。 |

## 严重程度分级

| 级别 | 含义 | 例子 |
|------|------|------|
| `error` | 文档误导——按文档操作会得到错误结果 | 文档说接口返回 `Result<Foo>` 但代码返回 `PageResult<Foo>`；文档说列类型是 `VARCHAR(64)` 但 DDL 是 `TEXT` |
| `warn` | 文档不全——读者得到的信息少于真实 | 代码新加了接口但文档没列；DDL 新加了列但文档没列 |
| `info` | 文档冗余/陈旧但无害 | 文档里列着一个已迁移路径的接口；文档里还提着一个已废弃的 DO |

**降级规则**（防刷屏）：

- Controller 方法上有 `@Deprecated` → 它的接口 diff 一律降为 `info`，不报 `warn`。
- 文档 §4.1 "API 中存在但无对应单表的实体声明" 章节内的条目 → 即使被命中
  `TABLE_MISSING_IN_DDL`，也降为 `info`（这些条目本来就是内存态/外部系统态，
  设计如此）。

## 报告格式

单份 markdown，章节顺序固定如下：

```markdown
# 文档漂移报告（Docs Drift Report）

_由 `docs-auto-sync` 生成（只读）。Files modified: 0。_

**Scope**: api+data ｜ **Domain**: admin+studio ｜ **Since**: 全量扫描 ｜ **Floor**: warn

## 汇总

| 类别 | error | warn | info | 合计 |
|------|------:|-----:|-----:|-----:|
| 接口：代码有文档无         | 0 | 3 | 0 | 3 |
| 接口：文档有代码无         | 0 | 1 | 2 | 3 |
| 接口：签名不一致           | 2 | 0 | 0 | 2 |
| 数据：表存在文档缺失       | 0 | 1 | 0 | 1 |
| 数据：字段类型不一致       | 1 | 0 | 0 | 1 |
| Entity ↔ DDL 映射不一致    | 0 | 2 | 0 | 2 |
| **合计**                   | **3** | **7** | **2** | **12** |

## 1. 接口

### 1.1 代码有但 docs/api-list.md 没记录（warn）

- **`POST /api/dataset/version/diff`** — `DatasetController.diffVersion`, line 187
  - suggested patch (paste manually, 追加到 docs/api-list.md §2 "数据集"):

    ```markdown
    | POST | /api/dataset/version/diff | 比较两个数据集版本的差异 |
    ```

### 1.2 文档里有但代码里找不到对应方法（info / warn）

- **`GET /api/legacy/foo`** — 出现在 docs/api-list.md L92，无匹配方法。
  - 可能在某次提交里被移除（用户可自行 git log 确认）；建议删除文档行。

### 1.3 签名不一致（error）

- **`GET /api/experiment/{id}`** — 文档写返回 `Result<Experiment>`，
  代码返回 `Result<ExperimentDetail>`（`ExperimentController.java:62`）。

## 2. 数据模型

### 2.1 DDL 有但 docs/data-model.md 没记录的表（warn）

- **`workflow_run`** — 定义于 `admin-schema.sql:412`，§2 未记录。
  - suggested patch (paste manually, 追加到 §2):

    ````markdown
    ### 2.7 workflow_run（工作流运行实例）
    | 字段 | SQL 类型 | 说明 |
    |------|----------|------|
    | id | BIGINT(20) UNSIGNED | 主键 |
    | ... | ... | ... |
    ````

### 2.2 字段类型不一致（error）

- 表 `dataset_item`，列 `content`：
  - DDL：`LONGTEXT NOT NULL`
  - docs/data-model.md L143：`TEXT`
  - 建议：把文档改为 `LONGTEXT NOT NULL`。

### 2.3 Entity ↔ DDL 映射不一致（warn）

- `DatasetItemDO.tags`（java 字段）↔ `dataset_item` 表里没有 `tags` 列。
  - 二选一：加列 + 写迁移脚本；或者从 DO 上把字段删掉。

## 3. 下一步（由人类决定）

本次运行 **0 file modifications**。请挑一个：

1. 按上面的 patch 手工应用。
2. 让另一个有写权限的 skill 或新对话来落盘——明确说"apply the
   docs-auto-sync report"，给 agent 显式授权。
3. 忽略。如果某条是有意为之的漂移，在对应 doc 加一行 inline 注释，
   下次跑会自动降级为 `info`。
```

上面的数字和条目都是示意，实际跑要填真实 finding；空章节直接省略；条目顺序
跨次运行保持稳定，方便 diff review。

## 常见坑

- **不要深度 diff 请求体字段**。文档基本不跟 DTO 重构同步，这种 finding 几乎
  全是噪声。只标 Step 4(c) 列举的四类签名级冲突。
- **不要过度归一化列类型**。`DATETIME` 与 `TIMESTAMP` 是真差异；`BIGINT(20)`
  与 `BIGINT` 是装饰差异。Step 7(d) 的归一化清单刻意做得很短，只有用户抱怨
  误报时才扩。
- **不要混淆两个域**。Admin DO 在 `server-start/admin/entity`；Studio Entity
  在 `server-core/.../core/base/entity`。同样的简单类名在两个域可以同时存在
  且指向不同表。
- **不要联动其他 skill**。即使某条 finding 一看就是 `scaffold-resource` 能修
  的，最多在报告里**写出 skill 名字提示**，绝不主动调起。
- **每条 finding 都必须带文件路径 + 行号**。整份报告的价值就是"去那里看"，
  没有定位信息的条目就是噪声。
- **不要因为 `severity_floor` 过滤后某个章节空了就静默删除**。渲染成
  `(no items at or above the current floor)`，让用户知道这部分确实扫过了。

## 什么时候应该跑一次

- 任何修改了 `*Controller.java`、`*DO.java` / `*Entity.java` 或
  `docker/middleware/init/mysql/*.sql` 的 PR 合入后。
- 自己开 PR 之前，特别是这次 PR 也动了 `docs/api-list.md` 或
  `docs/data-model.md`——确认是在"记录现实"，而不是"凭空发明"。
- 每周一次常规巡检。报告是只读的，成本基本是零。
