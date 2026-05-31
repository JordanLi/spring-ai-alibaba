# Spring AI Alibaba Admin — 运行环境清单

> **信源**：`docs/external-deps.svg` + `spring-ai-alibaba-admin-server-start/src/main/resources/application*.yml` + `docker/middleware/docker-compose-{dev,prod}.yaml` + `docker/middleware/mysql.env` + `pom.xml` + `README.md` + `model-config-*.yaml`。生成时间：2026-05-28。

跑起来这个项目最少需要 4 类东西：**Java/构建工具链** → **中间件** → **AI 模型 API Key**（至少一个） → **可选服务**。下面是逐项清单与初始化要求。

## 0. 版本快查表（按"非装不可"程度排序）

| 类别 | 组件 | 版本要求（主版本） | 必须? | 默认端口 | 备注 |
|------|------|------|------|----------|------|
| 工具链 | **JDK** | 17.x | ✅ 必须 | — | `pom.xml` `<java.version>17</java.version>`；Spring Boot 3.x 强制 JDK 17+ |
| 工具链 | **Maven** | 3.8+ | ✅ 必须 | — | README 写 3.8+；构建用的 `maven-compiler-plugin:3.9.0` |
| 工具链 | **Node.js** | 18+ | ✅ 仅前端 | — | `frontend/packages/main` 运行需要；后端服务不需要 |
| 工具链 | **Docker** + **Docker Compose** | Compose 2.0+ | 🟡 强烈推荐 | — | 不用 Docker 就要手装中间件，工作量大 |
| 中间件 | **MySQL** | 8.0.x | ✅ 必须 | 3306 | 配套镜像 `mysql:8.0.35`；JDBC 驱动 `mysql-connector-j:8.0.33` |
| 中间件 | **Redis** | 7.x | ✅ 必须 | 6379 | 配套镜像 `redis:7.2.5`；客户端 Redisson 3.27.x |
| 中间件 | **Elasticsearch** | 8.x（pom 客户端）/ 9.x（compose 服务） | ✅ 必须 | 9200, 9300 | ⚠️ 客户端 SDK 8.13.4 vs 服务端 9.1.2 跨大版本，需关注兼容性 |
| 中间件 | **Nacos** | 2.x（server）/ 2023.0.3.3（client） | ✅ 必须 | 8848, 9848, 8080 | ⚠️ prod compose 端口映射有 swap，见下文陷阱 |
| 中间件 | **RocketMQ** | 5.x | ✅ 必须 | 9876, 10909-12, 18080, 18081 | 配套镜像 `apache/rocketmq:5.3.2`；客户端走 **gRPC Proxy**（18080），不是传统 NameServer |
| 中间件 | **LoongCollector**（OTLP Receiver） | 3.x | 🟡 强烈推荐 | 4318 | 接收 trace 写入 ES；不装的话 observability 模块失效 |
| 中间件 | Kibana | 9.x | ⚪ 可选 | 5601 | 仅做 ES 数据可视化 |
| 外部 API | **AI 模型供应商** | — | ✅ 至少 1 个 | — | DashScope / OpenAI / DeepSeek / Ollama 任选 |
| 外部 API | Aliyun OSS | — | ⚪ 可选 | — | 对象存储；不用就配本地文件 |
| 外部 API | ARMS | — | ⚪ 可选 | — | `spring.ai.alibaba.arms.enabled` 控制 |

> **最小可运行集**：JDK 17 + Maven + MySQL（dev 模式只起 MySQL）+ 1 个模型 API Key。**完整功能集**：上表所有 ✅ + 🟡。

---

## 1. 工具链（宿主机）

### 1.1 JDK

| 项 | 值 |
|------|------|
| 版本 | **17.x**（任何 17 的次版本都行；建议 17.0.10+） |
| 强制约束 | `pom.xml:44-46`：`<java.version>17</java.version>`，`<maven.compiler.source/target>17</...>` |
| 检验 | `java -version` 输出含 `17.` |
| 升级到 21? | 当前 pom 没测试；Spring Boot 3.3.6 支持 21，但 spring-ai 1.1.2 + spring-ai-alibaba 1.0.0.3 未声明，建议先 17 |

### 1.2 Maven

| 项 | 值 |
|------|------|
| 版本 | **3.8+**（README 写明） |
| 检验 | `mvn -v` |
| 项目根 | 多模块 reactor，根 pom 在 `spring-ai-alibaba-admin/pom.xml`；revision `1.0.0-SNAPSHOT` |

### 1.3 Node.js（仅前端）

| 项 | 值 |
|------|------|
| 版本 | **18+**（README 未明确，按现代前端工具链推断） |
| 启动命令 | `cd frontend/packages/main && npm install && npm run dev` |
| 默认端口 | 8000（README 提到 http://localhost:8000） |

### 1.4 Docker

| 项 | 值 |
|------|------|
| Compose | **2.0+**（用 `docker compose` 子命令风格，不是旧的 `docker-compose`） |
| 推荐内存 | ≥ 8 GB（prod 模式跑全套中间件；ES 单独占 1 GB） |
| 启动入口 | `cd docker/middleware && ./run.sh prod`（或 `make env-start MODE=prod`） |

---

## 2. 中间件

> 全部中间件都可以通过 `docker/middleware/docker-compose-prod.yaml` 一键拉起；端口、镜像 tag、初始化脚本都写好了。下面**重点写应用侧需要知道的连接信息和初始化要求**，便于对接到非 Docker 环境（如 K8s、托管服务）。

### 2.1 MySQL

| 项 | 值 |
|------|------|
| **版本要求** | 8.0.x（compose 用 `8.0.35`） |
| **默认端口** | 3306 |
| **协议** | MySQL 原生 |
| **默认账户** | `admin` / `admin`（业务库账号）；`root` / `root`（root 账号）｜源：`docker/middleware/mysql.env` |
| **默认库名** | `admin`（应用唯一使用的库） |
| **JDBC URL 模板** | `jdbc:mysql://${HOST}:3306/admin?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&allowMultiQueries=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai` |
| **应用配置项** | `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` |
| **客户端** | `mysql-connector-j:8.0.33` + `druid-spring-boot-starter:1.2.20` + `mybatis-plus-spring-boot3-starter:3.5.9` |
| **初始化要求（重要）** | 启动时**必须**已执行：① `docker/middleware/init/mysql/admin-schema.sql`（评测/Prompt 域，12 张表）；② `docker/middleware/init/mysql/agentscope-schema.sql`（Studio 域，15 张表）。Docker 方式：脚本挂载到 `/docker-entrypoint-initdb.d/`，容器首次启动自动跑；**已存在 data 目录的容器不会再跑**——升级到新表需要手动 `mysql < xxx.sql`。 |
| **关键 schema 文件** | `docker/middleware/init/mysql/admin-schema.sql`、`docker/middleware/init/mysql/agentscope-schema.sql` |

### 2.2 Redis

| 项 | 值 |
|------|------|
| **版本要求** | 7.x（compose 用 `7.2.5`） |
| **默认端口** | 6379 |
| **协议** | RESP |
| **认证** | compose 中**未开密码**（生产必须改） |
| **默认库** | db 0（`SPRING_REDIS_DATABASE:0`） |
| **应用配置项** | `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT` / `SPRING_REDIS_DATABASE`（可选） |
| **客户端** | `redisson-spring-boot-starter:3.27.2` + spring-data-redis（Boot 自带） |
| **初始化要求** | 无；首次写入自动建 key |

### 2.3 Elasticsearch

| 项 | 值 |
|------|------|
| **版本要求** | **服务端** 8.x 或 9.x（compose 用 `9.1.2`）；**客户端** SDK 是 `co.elastic.clients:elasticsearch-java:8.13.4` |
| **默认端口** | 9200（HTTP/REST）、9300（transport，单节点用不到） |
| **安全** | compose 显式 **关掉了 xpack security**（`xpack.security.enabled=false`）。生产必须开 |
| **集群模式** | `discovery.type=single-node` |
| **JVM** | `ES_JAVA_OPTS=-Xms1g -Xmx1g`（compose 默认；不够可调） |
| **应用配置项** | `SPRING_ELASTICSEARCH_URIS`（spring.elasticsearch）、`SPRING_ELASTICSEARCH_URL`（自定义 ElasticsearchClient bean，见 `elasticsearch.yml`） |
| **客户端依赖** | `co.elastic.clients:elasticsearch-java`、`org.elasticsearch.client:elasticsearch-rest-client`、`spring-ai-elasticsearch-store`（spring-ai-bom） |
| **初始化要求（重要）** | 必须执行 `docker/middleware/init/elasticsearch/init-indices.sh`：① 创建 ingest pipeline `parsing_loongsuite_traces`（解析 OTLP trace JSON）；② 创建索引 `loongsuite_traces` 并绑定上面的 pipeline。**没这两步，可观测性模块查询会返回空。** Docker 方式：compose 里有独立的 `elasticsearch-init` 服务自动跑；非 Docker 部署需要手动执行同名脚本（脚本里的 `http://elasticsearch:9200` 改成实际地址）。 |
| **额外用途** | spring-ai vectorstore（知识库分片向量也可写入 ES，但 `docs/data-model.md` 注明：**document chunk 实际存在 ES，MySQL 里没有 `document_chunk` 表**） |

### 2.4 Nacos

| 项 | 值 |
|------|------|
| **版本要求** | **服务端** 2.x（compose 用 `nacos/nacos-server:latest`）；**客户端 spring-cloud-alibaba** 2023.0.3.3 |
| **默认端口** | 8848（主，HTTP/REST + gRPC 兼容）、9848（gRPC，2.x 新增）、8080（控制台） |
| **模式** | `MODE=standalone`（单机） |
| **认证 token** | `NACOS_AUTH_TOKEN=dG9rZW5hbHNka2ZqbGFza2RqZmxhc2tkamZsYXNrZGpmb3dpZWpmbztzZGxm`（compose 内置）+ `identity_key=admin` / `identity_value=admin` |
| **应用配置项** | `NACOS_SERVER_ADDR`（如 `localhost:8848`） |
| **客户端依赖** | `com.alibaba.cloud:spring-alibaba-nacos-config:2023.0.3.3` + nacos-client |
| **用途** | ① 动态配置中心（可选）；② 通过 `spring-ai-alibaba-agent-nacos` 代理给下游 Agent 应用做 Prompt 推送（README §6 提到） |
| **初始化要求** | 无（首次连接自动创建 default namespace）。如果要给业务做 namespace 隔离，需手动在 Nacos 控制台建 namespace 并把 namespace ID 配到 spring 配置里 |

> ⚠️ **Nacos 端口陷阱**（**踩过坑务必记下**）
> `docker-compose-prod.yaml` 里的 Nacos 端口映射 **不是直通**：
> ```yaml
> - "7080:8080"   # 控制台    宿主机 7080 → 容器 8080
> - "7848:8848"   # 主 HTTP   宿主机 7848 → 容器 8848   ← !!
> - "8848:9848"   # gRPC      宿主机 8848 → 容器 9848   ← !!
> ```
> 也就是说：**用 prod compose 启动时**，应用配 `nacos.server-addr: localhost:8848` 实际指向的是容器内 9848（gRPC），不是 8848（HTTP），会连接异常。
> 解决：要么改应用配置到 `localhost:7848`，要么改 compose 映射成直通 `8848:8848` / `9848:9848`。dev 模式不起 Nacos 不受影响。

### 2.5 RocketMQ

| 项 | 值 |
|------|------|
| **版本要求** | 5.x（compose 用 `apache/rocketmq:5.3.2`） |
| **默认端口** | 9876（NameServer）｜ 10909 / 10911 / 10912（Broker）｜ **18080 / 18081**（**gRPC Proxy**，应用实际连这里） |
| **客户端协议** | **gRPC**（不是传统 remoting）：`rocketmq-client-java:5.0.7`，新版 SDK |
| **应用配置项** | `ROCKETMQ_ENDPOINTS`（如 `localhost:18080`）、`ROCKETMQ_DOCUMENT_INDEX_TOPIC`（默认 `topic_saa_studio_document_index`）、`ROCKETMQ_DOCUMENT_INDEX_GROUP`（默认 `group_saa_studio_document_index`） |
| **初始化要求（重要）** | 必须创建 ① topic `topic_saa_studio_document_index`；② consumer group `group_saa_studio_document_index`。Docker 方式：compose 里有独立的 `init-topic` 服务用 `mqadmin` 自动跑（等 60s 后执行）；非 Docker 部署需手动跑 `sh mqadmin updateTopic -n ${NAMESRV}:9876 -t topic_saa_studio_document_index -c DefaultCluster` 等命令 |
| **用途** | 知识库文档分片索引任务的异步派发（document 索引到 ES 的 worker pipeline） |
| **proxy 配置** | `docker/middleware/conf/rocketmq/rmq-proxy.json` 决定 Proxy 连哪个 namesrv |

### 2.6 LoongCollector（OTLP Receiver）

| 项 | 值 |
|------|------|
| **版本要求** | 3.x（compose 用 `loongcollector:3.1.4`） |
| **默认端口** | 4318（**OTLP/HTTP** 接收） |
| **应用配置项** | `MANAGEMENT_OTLP_TRACING_EXPORT_ENDPOINT`（如 `http://localhost:4318/v1/traces`） |
| **管道配置** | `docker/middleware/conf/loongcollector/otlp_pipeline.yaml`（OTLP → ES 写入） |
| **依赖关系** | depends_on `elasticsearch-init`（确保 `loongsuite_traces` 索引已建） |
| **用途** | 接收应用上报的 OpenTelemetry traces，写入 ES 的 `loongsuite_traces` 索引；后续 `/api/observability/*` 接口从 ES 查 |
| **能换成谁** | 任何符合 OTLP HTTP 协议的 Collector（如 Jaeger、Tempo、OTel Collector）都行，只要最终目的写到同名 ES 索引 |
| **不装会怎样** | 应用启动不会失败（导出失败被吞），但 `/api/observability/*` 接口永远返回空 |

---

## 3. 外部 API（AI 模型供应商）

至少配 1 个，多个都配也行（model_config 表里可以存多条）。

| 提供商 | env var | base URL | 初始化要求 |
|--------|---------|----------|-----------|
| **DashScope**（阿里云百炼） | `DASHSCOPE_API_KEY` | `https://dashscope.aliyuncs.com/compatible-mode` | 配 `spring-ai-alibaba-admin-server-start/model-config.yaml`（参考 `model-config-dashscope.yaml`）；启动时 ModelConfig 会从这个 yaml 引导入库 |
| **OpenAI** | `OPENAI_API_KEY` | `https://api.openai.com/v1` | 同上，参考 `model-config-openai.yaml` |
| **DeepSeek** | `DEEPSEEK_API_KEY` | `https://api.deepseek.com` | 同上，参考 `model-config-deepseek.yaml` |
| **Ollama**（本地） | 无（按 base URL 直连） | `http://localhost:11434`（默认） | 本地装 Ollama + `ollama pull <model>`；适合无 API Key 环境 |

> 💡 DashScope 申请：[Alibaba Cloud Bailian Console](https://bailian.console.aliyun.com/?tab=model#/api-key)。
>
> SDK 依赖：`spring-ai-openai`（用 OpenAI 兼容协议接 DashScope/DeepSeek）、`spring-ai-ollama`，均通过 `spring-ai-bom:1.1.2` 引入；自动配置在 `spring-ai-autoconfigure-model-*`。

---

## 4. 可选服务

### 4.1 Aliyun OSS

| 项 | 值 |
|------|------|
| **客户端** | `aliyun-sdk-oss:3.17.4` |
| **何时需要** | 知识库文档原文/解析产物存对象存储；不用就用本地路径 |
| **配置** | 凭证（AK/SK）+ Bucket + Endpoint，应用读取 `oss.*` 节点（具体在 `core.base.service` 实现） |

### 4.2 ARMS Observation

| 项 | 值 |
|------|------|
| **开关** | `spring.ai.alibaba.arms.enabled=true` |
| **依赖** | `spring-ai-alibaba-autoconfigure-arms-observation` |
| **何时需要** | 接入阿里云 ARMS 做调用链/Token 用量观测；公开 OTLP 走 LoongCollector 即可，不依赖 ARMS |

---

## 5. 启动顺序 & 健康检查

最小可运行（dev 模式，只起 MySQL）：

```bash
# 1. 启 MySQL（首次会自动跑 init/mysql/*.sql）
cd docker/middleware && ./run.sh dev

# 2. 编译后端
cd ../../ && ./mvnw -B package -DskipTests=true

# 3. 启后端（用 local profile，连 localhost）
cd spring-ai-alibaba-admin-server-start
mvn spring-boot:run -Dspring-boot.run.profiles=local
# 启动前还要先 export DASHSCOPE_API_KEY=...（或对应供应商）
```

完整运行（prod 模式，所有中间件）：

```bash
# 1. 启全部中间件（约 2-3 min，等 init-topic / elasticsearch-init 跑完）
cd docker/middleware && ./run.sh prod

# 等以下三个 init 容器进入 exited(0) 才算 OK：
#   - elasticsearch-init      创建 loongsuite_traces 索引
#   - rmq-init-topic          创建 topic + group

# 2. 改 application-local.yml 的 nacos.server-addr 为 localhost:7848（绕开端口陷阱）

# 3. 同上启后端 + 至少一个模型 env var
```

健康检查端点：

| 服务 | 检查命令 |
|------|---------|
| MySQL | `mysqladmin -h 127.0.0.1 -P 3306 -uadmin -padmin ping` |
| Redis | `redis-cli -h 127.0.0.1 -p 6379 ping` → `PONG` |
| Elasticsearch | `curl http://127.0.0.1:9200/_cluster/health` → `status: green/yellow` |
| ES 索引 | `curl http://127.0.0.1:9200/loongsuite_traces/_mapping`（200 = init 成功） |
| Nacos | `curl http://127.0.0.1:7080/nacos/`（200 / 302）｜直通映射时是 `:8848` |
| RocketMQ Proxy | `nc -zv 127.0.0.1 18080`（端口可达即可，gRPC 没 HTTP healthz） |
| LoongCollector | `nc -zv 127.0.0.1 4318` |
| 后端 | `curl http://127.0.0.1:8080/actuator/health` |

---

## 6. 常见踩坑速查

| 现象 | 真因 | 处置 |
|------|------|------|
| 后端启动报 `Communications link failure` | MySQL 没起 / `init/mysql/*.sql` 没跑 / 端口冲突 | 先 `mysql -uadmin -padmin -e 'show tables from admin'`；表为空就手动 source SQL |
| 应用启动报找不到 `loongsuite_traces` | ES 起来了但 init 脚本没跑 | 手动 `sh docker/middleware/init/elasticsearch/init-indices.sh`（把 `elasticsearch:9200` 改成实际地址） |
| 应用启动卡在 Nacos 连接超时 | prod compose 端口映射 swap，`8848` 实际是 gRPC | 改 `nacos.server-addr` 到宿主机的 `7848` 或修正 compose 端口 |
| 文档分片任务不动 / `document.index_status` 一直是 1 | RocketMQ topic 没建 | 手动 `sh mqadmin updateTopic -n localhost:9876 -t topic_saa_studio_document_index -c DefaultCluster` |
| `/api/observability/traces` 永远返回空 | LoongCollector 没起 / 端口被占 / 应用没正确导出 trace | 确认 `MANAGEMENT_OTLP_TRACING_EXPORT_ENDPOINT` 配的是 `:4318/v1/traces`（带 `/v1/traces` 路径） |
| 模型调用 401/403 | API Key 没设 env var / model-config.yaml 没引导导入数据库 | `echo $DASHSCOPE_API_KEY`；查 `select * from model_config` 是否有该模型记录 |
| ES 启动 OOM | `ES_JAVA_OPTS` 太大或宿主机内存不足 | 改 compose `ES_JAVA_OPTS=-Xms512m -Xmx512m` |
| `mvn spring-boot:run` 报 Java 版本不对 | JDK 不是 17 | `java -version` 确认；macOS 用 `jenv` 或 `JAVA_HOME` 切换 |

---

## 7. 改动这份清单的时机

- 任何一项中间件镜像 tag 在 `docker-compose-*.yaml` 中变更
- `pom.xml` 里 `<*.version>` 属性改了主版本
- 新加了 env var 或新依赖了一个外部服务
- `init/*` 目录里新加了初始化脚本

建议每次升级中间件 / 新增依赖时把这份 checklist 一并 review，避免文档和现实脱节。
