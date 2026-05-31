---
name: env-bootstrap
description: >-
  End-to-end local environment bootstrap & health verification for
  spring-ai-alibaba-admin on macOS: inventory dependencies, install/start
  middleware (MySQL, Redis, Elasticsearch+Kibana, Nacos, RocketMQ,
  LoongCollector) via the repo scripts, build & run the Spring Boot app with
  the local profile, and smoke-test core REST APIs (login / Prompt / Dataset /
  Evaluator / Trace). Use when onboarding to the project for the first time,
  resetting or rebuilding the local environment, or periodically verifying
  environment health. Trigger on requests like 新接手项目搭环境, 重置本地环境,
  环境健康检查, 把依赖都装上跑起来, set up the local dev environment, verify
  the environment is healthy, bootstrap middleware and start the app. Prefer
  this over ad-hoc manual setup because it reuses the project's hardened
  scripts (install-deps.sh / deps-start.sh / deps-status.sh / deps-stop.sh)
  and encodes the known macOS/Docker pitfalls and their fixes.
allowed-tools: [Read, Bash, Write]
---

# env-bootstrap

一键把 `spring-ai-alibaba-admin` 的本地开发环境从零搭好并验证健康。按 5 步推进，
每步都先复用仓库已有脚本，再用验证手段确认通过，失败时遵循**自主修复原则**
（同一错误连续 3 次仍无法解决才停下汇报）。

## 何时使用

- **新接手项目**：第一次在本机把环境跑起来。
- **重置环境**：依赖坏了 / 想干净重来。
- **定期健康检查**：确认中间件 + 应用 + 核心接口仍可用。

## 前置假设

- macOS（脚本基于 `brew` + Docker Desktop；Linux 需另行适配）。
- 已装 Homebrew 和 Docker Desktop（或 OrbStack）。
- 工作目录为 `spring-ai-alibaba-admin/`。
- 如需国际源加速：`export https_proxy=... http_proxy=... all_proxy=...`（`install-deps.sh` 会自动注入并探测可达性）。

---

## 步骤一：依赖盘点

目的：搞清要装什么、端口/版本、初始化要求，再动手。

1. 读 `docs/env-checklist.md`（依赖、端口、版本、初始化要求的权威清单）。
2. 读 `scripts/install-deps.sh` 头部注释与 `docs/setup-guide.md`（若存在）确认范围。
3. 列出目标组件与端口（核对用）：

   | 组件 | 端口 | 备注 |
   |---|---|---|
   | JDK 17 / Maven | — | brew，keg-only 需写 PATH |
   | MySQL 8.0 | 3306 | 建 `admin` 库 + `admin/admin` 用户 + 建表 |
   | Redis 7 | 6379 | |
   | Elasticsearch 9.1.2 | 9200/9300 | 初始化 `loongsuite_traces` 索引 |
   | Kibana 9.1.2 | 5601 | |
   | Nacos v3.2.1 | 7080(console)/7848(API)/8848(gRPC) | standalone |
   | RocketMQ 5.3.2 | 9876 / 10909-10912 / 18080(remoting)/18081(gRPC) | topic+group 初始化 |
   | LoongCollector 3.1.4 | 4318 | OTLP |

---

## 步骤二：安装中间件（含初始化 + 验证）

用 `scripts/install-deps.sh`，它对每个组件做「装 + 初始化 + 验证」三步，并生成报告。

```bash
# 先小范围试探网络/代理是否通：
./scripts/install-deps.sh --only=jdk,maven
# 再全量安装（也可分批 --only=mysql,redis / --only=docker,compose）：
./scripts/install-deps.sh
```

完成后：

- 读 `scripts/install-deps-log.md`（自动生成的安装报告：每个组件的命令/版本/端口/最终验证 + 本次 WARN/ERR）。
- `source ~/.zshrc` 让 `JAVA_HOME` / `PATH` 生效。

**已内置的已知坑（脚本已自动处理，无需手动干预）**：

- 国际源慢 → 注入 `http_proxy/https_proxy/all_proxy` 并探测可达。
- RocketMQ 权限死循环重启 → 生成 `.env`(UID=3000/GID=3000) + `docker-compose-override-macos.yaml` 固定 `user: 3000:3000`。
- ES OOM(exit137) / Kibana JS heap OOM → override 降 ES 堆到 512m、Kibana `--max-old-space-size=768`。
- keg-only(java/mysql) 不在 PATH → 安装后写 `~/.zshrc` 并运行内即时 export。
- `mqadmin` 不在容器 PATH → 动态探测路径；topic/group 优先看 `rmq-init-topic` 日志兜底。

---

## 步骤三：启停脚本（启动并等就绪）

```bash
./scripts/deps-start.sh    # 一键启动全部中间件，按依赖顺序并等就绪
./scripts/deps-status.sh   # 查看每个组件 READY/端口/探测详情
./scripts/deps-stop.sh     # 一键停止全部中间件
```

要点：

- `deps-start.sh` 已做**分阶段启动**：ES → ES 索引初始化 → Nacos(API ready) →
  **NameServer+Broker（等 Broker healthy）→ Proxy+init-topic** → LoongCollector。
  其中「等 Broker healthy 后再起 Proxy」是关键：否则 Proxy 早启动会建系统主题失败、
  进入降级态，导致后续 RocketMQ gRPC 客户端 `DEADLINE_EXCEEDED`。
- 启动结束会自动调用 `deps-status.sh`，**确认 10 个组件全部 `OK` 再继续**。
- 偏好纯 Docker 的同学可用根目录 `docker-compose.dev.yml`（把应用也放进同一 docker 网络，
  规避「宿主访问容器内网地址」的问题）。

---

## 步骤四：编译并启动应用

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"

# 构建（多模块 reactor，首次约 10+ 分钟）
./mvnw clean package -DskipTests   # 在 spring-ai-alibaba-admin/ 下

# 启动（local profile 已内置 localhost 连接默认值）
java -jar spring-ai-alibaba-admin-server-start/target/spring-ai-alibaba-admin-server-start.jar \
  --spring.profiles.active=local
```

启动成功标志：日志出现 `Started SaaStudioAdmin ...` 与 `Tomcat started on port 8080`。

**已知坑与排查（曾出现过，遇到对症处理）**：

- 编译报「枚举构造器/getter 缺失」且只在 `server-runtime`：先去掉 `-q` 看**最早**的根错误。
  曾遇到 `domain/plugin/ToolExample.java` 被错放的教程代码覆盖（包名/类名不符、缺依赖），
  parse 阶段报错打断了 Lombok 注解处理 → 连带枚举二次报错。不要先怀疑 Lombok 版本。
  修复：从 git 历史恢复该领域类 `git show <good-commit>:<path> > <path>`。
- 启动时 RocketMQ producer `DEADLINE_EXCEEDED`：
  1. 确认端点是 gRPC 口 **18081**（`rmq-proxy.json: grpcServerPort`），不是 remoting 的 18080；
     `application-local.yml` 的 `rocketmq.endpoints` 应为 `localhost:18081`。
  2. 若已连上 proxy 仍超时，多半是 proxy 在 broker 就绪前启动而降级 → 重启 proxy：
     `cd docker/middleware && docker compose -f docker-compose-prod.yaml -f docker-compose-override-macos.yaml restart rmq_proxy`，
     日志出现 `rocketmq-proxy startup successfully` 即恢复（步骤三的新版 deps-start.sh 已避免）。

**应用访问地址**：

- 后端 + 内置前端：http://localhost:8080/
- 健康检查：http://localhost:8080/actuator/health
- API 文档：http://localhost:8080/swagger-ui/index.html

**（可选）独立前端 dev server**（umi monorepo，端口 8000，代理 `/api /console /oauth2` → 8080）：

```bash
cd frontend
npm install --ignore-scripts   # 跳过会因 spark-flow/dist 缺失而失败的 postinstall
npm run build:flow             # 先构建 spark-flow（main 通过 alias 引用其 dist）
cd packages/main && npm run dev # http://localhost:8000  默认登录 saa/123456
```

---

## 步骤五：核心接口冒烟

目的：验证鉴权链路 + 各业务模块接口可用。判定：**HTTP 200 算通过**。

```bash
# 1) 登录拿 token（账号 saa/123456）
curl -s -X POST http://localhost:8080/console/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"saa","password":"123456"}' -o /tmp/login.json
TOKEN=$(node -e "console.log(require('/tmp/login.json').data.access_token)")
A="Authorization: Bearer $TOKEN"

# 2) Prompt / 3) Dataset / 4) Evaluator
curl -s -o /dev/null -w "prompt    %{http_code}\n" -H "$A" "http://localhost:8080/api/prompts?current=1&pageSize=10"
curl -s -o /dev/null -w "dataset   %{http_code}\n" -H "$A" "http://localhost:8080/api/dataset/datasets?current=1&pageSize=10"
curl -s -o /dev/null -w "evaluator %{http_code}\n" -H "$A" "http://localhost:8080/api/evaluator/evaluators?current=1&pageSize=10"

# 5) Trace —— 必须带 startTime/endTime（epoch 毫秒），否则 400 是参数校验不是故障
NOW=$(node -e "console.log(Date.now())"); AGO=$(node -e "console.log(Date.now()-3600000)")
curl -s -o /dev/null -w "trace     %{http_code}\n" -H "$A" \
  "http://localhost:8080/api/observability/traces?current=1&pageSize=10&startTime=$AGO&endTime=$NOW"
```

把每个接口的 HTTP 码汇总，200 标 ✅，非 200 列出并附 message。完整接口清单见 `docs/api-list.md`，
可参考已有的 `docs/smoke-test-result.md` 输出格式生成结果文档。

---

## 验证清单（全部满足才算环境健康）

- [ ] `./scripts/deps-status.sh` → 10 个组件全部 `OK`。
- [ ] MySQL `admin` 库存在且建表完成：`mysql -h127.0.0.1 -uadmin -padmin admin -e "SHOW TABLES;"`（约 27 张表）。
- [ ] ES 索引就绪：`curl -fsS http://localhost:9200/loongsuite_traces/_mapping` 返回 200。
- [ ] RocketMQ topic 就绪：`docker logs rmq-init-topic` 含 `topic_saa_studio_document_index`。
- [ ] 应用启动：日志含 `Started SaaStudioAdmin` 且 `Tomcat started on port 8080`。
- [ ] 健康检查：`GET http://localhost:8080/actuator/health` → `{"status":"UP"}`（db/elasticsearch/redis 均 UP）。
- [ ] 首页可访问：`GET http://localhost:8080/` → 200。
- [ ] 5 个核心接口冒烟全部 200（登录/Prompt/Dataset/Evaluator/Trace）。

## 收尾 / 停止

```bash
pkill -f spring-ai-alibaba-admin-server-start.jar   # 停后端
pkill -f "umi dev"                                  # 停前端（如启了）
./scripts/deps-stop.sh                              # 停全部中间件
```

## 参考文档

- `docs/env-checklist.md` —— 依赖/端口/版本权威清单
- `scripts/install-deps-log.md` —— 安装报告（自动生成）
- `docs/startup-log.md` —— 编译/启动全过程与踩坑记录
- `docs/smoke-test-result.md` —— 接口冒烟结果样例
- `docs/setup-guide.md` —— 新人上手指南
