---
name: scaffold-resource
description: >-
  Scaffolds a new business resource end-to-end in spring-ai-alibaba-admin:
  generates Entity/DO, Mapper (with XML for admin domain), Service+Impl,
  Controller, and request/response DTOs; appends a matching CREATE TABLE to
  the right schema sql; and adds placeholder rows in docs/data-model.md and
  docs/api-list.md. Handles both persistence conventions that coexist in the
  repo: admin-domain (JPA-style DO + MyBatis Mapper.xml under
  server-start/admin) and studio-domain (MyBatis-Plus Entity + BaseMapper
  under server-core/core/base with controller under builder/controller). Use
  when the user asks to add a new business resource, a new table plus CRUD
  endpoints, a new persistent domain object, or says 新增 XX 模块,
  加一张表配套 controller+service+mapper, 添加业务资源, scaffold a new
  resource, or new CRUD resource. Prefer this over generic CRUD generators
  because it follows the repo's two existing conventions exactly and keeps
  DDL and docs in sync.
allowed-tools: [Read, Glob, Grep, Write, StrReplace, Shell]
---

# Scaffold Resource

## What this skill does

Produces a coherent, project-conformant 5-piece stack for a new business resource and keeps DDL + docs aligned, so the new resource compiles, runs, and surfaces in `docs/data-model.md` + `docs/api-list.md` in one pass.

The repository has **two coexisting conventions**. Picking the wrong one is the single most common scaffolding mistake, so the first real decision below is **which domain**.

## Inputs to gather

Ask once, in a single round:

| Parameter | Required? | Example |
|-----------|-----------|---------|
| `name` (PascalCase) | yes | `Workflow` / `Knowledge` |
| `tableName` (snake_case) | yes | `workflow` / `knowledge_base` |
| `domain` | yes — `admin` or `studio` | see decision rules below |
| `fields` | yes — list of `(name, sqlType, nullable?, default?, enum?, index? uk?)` | `template:LONGTEXT NOT NULL`, `status:VARCHAR(32) DEFAULT 'DRAFT'` |
| `urlPrefix` | optional, has defaults | admin → `/api/<resource>`, studio → `/console/v1/<resources>` |
| `multiTenant` | optional, default per domain | adds `workspace_id` + LFK |
| `softDelete` | optional, default false | adds `deleted TINYINT(1) DEFAULT 0` (admin) or `status TINYINT` (studio) |
| `paged` | optional, default true | exposes `/page` / `?current=&pageSize=` |
| `versioned` | optional, default false | also scaffolds `XxVersion` child + FK |
| `refresh` | optional, default true | chain `refresh-data-model` + `refresh-api-docs` at the end |

If the user only gave a name and a vague request, ask the **minimum** needed to disambiguate domain + a starter field set. Don't pad the questionnaire.

## Decide the domain

Use this rule, in order:

1. **Direct hint** — if the user says "评测 / 评估 / Prompt / 实验 / 模型配置" → `admin`. If "应用 / 知识库 / 工具 / 插件 / MCP / Agent 编排 / 工作区" → `studio`.
2. **Existing siblings** — if the new resource is conceptually paired with an existing entity, follow that sibling's domain. Examples:

   | Sibling examples | Domain |
   |------------------|--------|
   | `dataset`, `prompt`, `evaluator`, `experiment`, `model_config` | `admin` |
   | `tool`, `plugin`, `mcp_server`, `application`, `agent_schema`, `knowledge_base`, `document`, `workspace`, `account` | `studio` |

3. **API surface** — exposed to **external OpenAPI/SDK** consumers → `admin` (`/api/...`). Exposed to **internal console / Studio UI** → `studio` (`/console/v1/...`).
4. If still unclear, ask the user to pick — do **not** guess silently.

## File layout per domain

`admin` domain — everything under `spring-ai-alibaba-admin-server-start/`:

```
src/main/java/com/alibaba/cloud/ai/studio/admin/
├── entity/{{Name}}DO.java
├── mapper/{{Name}}Mapper.java
├── service/{{Name}}Service.java
├── service/impl/{{Name}}ServiceImpl.java
├── controller/{{Name}}Controller.java
└── dto/
    ├── {{Name}}.java                       # 领域 DTO（出参）
    └── request/{{Name}}{Create,Update,List}Request.java
src/main/resources/mapper/{{Name}}Mapper.xml
docker/middleware/init/mysql/admin-schema.sql       # append CREATE TABLE
```

`studio` domain — split across `server-core` (data + service) and `server-start` (controller):

```
spring-ai-alibaba-admin-server-core/src/main/java/com/alibaba/cloud/ai/studio/core/base/
├── entity/{{Name}}Entity.java
├── mapper/{{Name}}Mapper.java
├── service/{{Name}}Service.java
└── service/impl/{{Name}}ServiceImpl.java

spring-ai-alibaba-admin-server-start/src/main/java/com/alibaba/cloud/ai/studio/admin/builder/controller/
└── {{Name}}Controller.java

docker/middleware/init/mysql/agentscope-schema.sql  # append CREATE TABLE
```

## Workflow

Each step lists the tool to use and the concrete action. Steps run sequentially; do not skip.

| # | Step | Tool | Concrete action |
|---|------|------|-----------------|
| 1 | Gather inputs | (chat) | Ask the user **one** consolidated question covering every "yes" row in the Inputs table; for optional rows use the defaults silently |
| 2 | Decide domain | (chat) | Apply the 4 rules in "Decide the domain"; print the chosen domain + planned file paths once, then proceed (no extra confirmation unless rule 4 fired) |
| 3 | Read a sibling | `Read`, `Glob` | Open one existing entity in the chosen domain (`ToolEntity` for studio, `PromptDO` for admin) — mirror its first 20 lines (package, license-or-no-license, imports, lombok set) |
| 4 | Create dirs | `Shell` | `mkdir -p` every target directory listed in "File layout per domain" |
| 5 | Write Entity/DO | `Write` | Use the template from the matching `references/<domain>-domain.md` |
| 6 | Write Mapper | `Write` | admin → interface + XML (both files); studio → `BaseMapper<XxEntity>` interface only |
| 7 | Write Service + Impl | `Write` | admin → plain interface + `@Service` impl; studio → `extends IService<XxEntity>` + `extends ServiceImpl<XxMapper, XxEntity>` |
| 8 | Write DTOs | `Write` | `XxCreateRequest` / `XxUpdateRequest` / `XxListRequest` (+ `Xx` domain DTO for admin) — Lombok `@Data` + Bean Validation annotations |
| 9 | Write Controller | `Write` | URL prefix defaults: admin = `/api/<resource>`, studio = `/console/v1/<resources>`; return `Result<T>` / `PagingList<T>` |
| 10 | Append DDL | `StrReplace` | Append the rendered CREATE TABLE block to the right `*-schema.sql`; do not edit existing tables |
| 11 | License header | (skipped per rule) | Only studio files needed it — already inserted in step 5–9; admin files stay header-less (see §License header rule). Verify by re-reading first 20 lines of a created file |
| 12 | Docs placeholder | `StrReplace` | Insert one subsection in `docs/data-model.md` (§2 admin / §3 studio) and one section in `docs/api-list.md`. Skeleton only; `refresh-*` will fill them |
| 13 | Compile-check | `Shell` | `mvn -q -pl <module> -am compile`; on failure, fix per "Common compile errors" then retry once |
| 14 | Refresh docs | `Shell` / (chain) | If `refresh != false` (default true): invoke `refresh-data-model` and `refresh-api-docs` skills to replace placeholders with real generated content |
| 15 | Report | (chat) | Print created/modified file list (grouped by java/xml/sql/md), the DDL block for review, and the suggested commit message from "Output to the user" |

## Code templates

The full Java/XML/SQL templates live in two reference files. Read whichever matches the chosen domain:

- `admin` domain → see [references/admin-domain.md](references/admin-domain.md)
- `studio` domain → see [references/studio-domain.md](references/studio-domain.md)

Do **not** mix the two — JPA `@Table` on a studio entity, or `@TableName` on an admin DO, both compile-pass quietly but break runtime queries.

## DDL conventions

Append the new `CREATE TABLE` to the right schema file, matching tone + style of existing tables. A copyable template:

```sql
CREATE TABLE IF NOT EXISTS `{{table_name}}` (
  `id` BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  -- {{ for each field }}
  `{{snake_field}}` {{SQL_TYPE}} {{NULL/NOT NULL}} {{DEFAULT ...}} COMMENT '{{cn description}}',
  -- {{ end for }}
  {{ if multiTenant }} `workspace_id` VARCHAR(64) NOT NULL COMMENT '工作区 ID', {{ endif }}
  {{ if softDelete admin }} `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除: 0 未删 / 1 已删', {{ endif }}
  {{ if softDelete studio }} `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0 删除 / 1 正常', {{ endif }}
  `gmt_create` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  {{ for each uk field }} UNIQUE KEY `uk_{{name}}` (`{{name}}`), {{ end for }}
  {{ for each idx field }} KEY `idx_{{name}}` (`{{name}}`) {{ end for }}
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='{{ resource cn name }}';
```

Notes:
- `admin` tables historically use `create_time` / `update_time` instead of `gmt_create` / `gmt_modified` — check the sibling table in the same domain and **match it** rather than mixing.
- Always add an index for any `*_id` foreign or logical-foreign column.

## License header rule (subtle but important)

The repo is inconsistent on purpose:

| Domain | License header on `.java`? |
|--------|----------------------------|
| `studio` (server-core/* + builder/controller/*) | **Required.** Apache 2.0, year matches sibling files (`2025` is the common value). |
| `admin` (server-start/admin/entity/, mapper/, controller/, service/, dto/) | **Not present** in the existing sibling files; do not add one to keep the new file consistent. |
| All `.sql` segments | None of the existing CREATE TABLE blocks carry per-table headers; the schema file has a file-level header already — don't duplicate. |

When in doubt, **read one existing sibling file in the same package and mirror its first 20 lines verbatim** (license or no license).

## Docs placeholder update

In `docs/data-model.md`:
- Insert a `### x.y {{tableName}}（{{cn name}}）` subsection in the right top-level section (§2 评测 for admin, §3 Studio for studio).
- Include the three-column `字段 | SQL 类型 | 说明` table and a `键与约束摘要` line. Don't try to be exhaustive — `refresh-data-model` will rebuild this from DDL + entity later.

In `docs/api-list.md`:
- Insert a `## n. {{cn name}}（{{urlPrefix}}）` section after the appropriate sibling.
- Include rows for the CRUD endpoints generated in step 7 only. `refresh-api-docs` will rebuild from the actual controller later.

If `refresh=true` (default), invoke `refresh-data-model` and `refresh-api-docs` after step 12 so the placeholders are replaced with the real generated content before commit.

## Verification commands (step 13)

```bash
# admin domain
mvn -q -pl spring-ai-alibaba-admin-server-start -am compile

# studio domain (entity/service in core, controller in start)
mvn -q -pl spring-ai-alibaba-admin-server-core -am compile
mvn -q -pl spring-ai-alibaba-admin-server-start -am compile
```

## Common compile errors

- Missing `@Mapper` (admin) or missing `extends BaseMapper<XxEntity>` (studio).
- Wrong unified response type imported (`R<T>` from generator vs `Result<T>` from runtime). Controllers in `admin/controller/` and `admin/builder/controller/` use `com.alibaba.cloud.ai.studio.runtime.domain.Result`; only `admin/builder/generator/controller/*` uses `R<T>`.
- DTO `Request` classes missing Lombok `@Data` / Bean Validation annotations referenced by `@Validated`.
- Studio service forgot `extends ServiceImpl<XxMapper, XxEntity>`.

## Output to the user

When done, print:
1. The list of created/modified files (grouped: java, xml, sql, md).
2. The `CREATE TABLE` DDL inserted (so the user can sanity-check column types and indexes).
3. A suggested next command for the `prepare-pr` skill:

```
feat({{scope}}): add {{name}} resource (entity + mapper + service + controller + ddl)
```

Where `{{scope}}` is `admin` for either domain — `lint-pr-title.yml` constrains scopes to a closed list that does **not** include `studio`/`core`; `admin` is the umbrella scope for this sub-project.

## Pitfalls to avoid

- **Mixing the two persistence stacks.** A studio Entity using JPA annotations or an admin DO using MyBatis-Plus annotations will compile but won't be wired by either persistence layer.
- **Writing into the wrong module.** `studio` resources put data layer in `server-core` and controller in `server-start`; putting the controller in `server-core` orphans it (server-core has no `@RestController`).
- **Skipping the DDL change.** Without `CREATE TABLE`, the new entity throws at first query in `mvn spring-boot:run`. Always append, don't replace existing tables.
- **Auto-adding a license header to admin/ files** because every other file in the repo has one — admin sub-tree doesn't, and the linter doesn't check it for that path.
- **Picking a scope that `lint-pr-title.yml` rejects.** Allowed scopes for this repo include `admin`; `studio`, `core`, `openapi` are **not** in the allowed list and the PR title check will fail.
