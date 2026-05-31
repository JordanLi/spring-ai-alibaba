# Spring AI Alibaba Admin 本地搭建指南（新人版）

面向第一次在本机（macOS）把 `spring-ai-alibaba-admin` 跑起来的同学。
照着做即可：装中间件 → 起中间件 → 编译 → 启动后端 →（可选）起前端 → 验证。

> 本指南由 `scripts/install-deps-log.md` 与 `docs/startup-log.md` 的实跑记录整理而成，
> 命令、端口、踩坑都来自真实环境。

---

## 一、前置条件

| 项 | 要求 | 说明 |
|---|---|---|
| 操作系统 | macOS（Apple Silicon / Intel） | 安装脚本目前只适配 macOS + Homebrew；Linux 需自行用 apt 等价替换 |
| Homebrew | 已安装 | 装中间件、JDK、Maven 都靠它 |
| Docker Desktop | 已安装并能启动 | ES / Kibana / Nacos / RocketMQ / LoongCollector 都跑在 Docker 里 |
| 内存 | 建议 Docker Desktop ≥ 6 GB | ES + Kibana + RocketMQ 同时跑较吃内存 |
| 网络 | 能访问 Docker Hub / Maven 中央仓库 | 国内网络建议配代理，见下方「踩坑」 |
| JDK | **17**（脚本会装 `openjdk@17`） | 项目强制 Java 17 |
| Node | **18+**（仅前端需要） | 后端不依赖 Node |

会被自动安装/拉起的组件与版本：

| 组件 | 版本 | 端口 | 管理方式 |
|---|---|---|---|
| JDK | openjdk@17 | — | brew（keg-only） |
| Maven | 3.9.x | — | brew |
| MySQL | 8.0 | 3306 | brew services |
| Redis | 7 | 6379 | brew services |
| Elasticsearch | 9.1.2 | 9200 / 9300 | Docker |
| Kibana | 9.1.2 | 5601 | Docker |
| Nacos | v3.2.1 | 7080(控制台) / 7848(API) / 8848(gRPC) | Docker |
| RocketMQ | 5.3.2 | 9876 / 10911 / 18080(remoting) / 18081(gRPC) | Docker |
| LoongCollector | 3.1.4 | 4318 (OTLP) | Docker |

---

## 二、装中间件（一次性）

在项目根目录 `spring-ai-alibaba-admin/` 下执行：

```bash
./scripts/install-deps.sh
```

脚本做了三件事：**装 + 初始化 + 验证**（建 MySQL `admin` 库与 `admin/admin` 账号、
ES 建 `loongsuite_traces` 索引、RocketMQ 建 topic/group 等）。它遵循自主修复原则，
大多数常见问题会自己处理。

可选用法：

```bash
./scripts/install-deps.sh --only=jdk,maven     # 只装部分，先探网络
./scripts/install-deps.sh --skip=docker         # 跳过某些组件
```

运行结束会生成 `scripts/install-deps-log.md`（结构化报告：每个组件最终命令、版本、
端口、验证状态），完整流水另存于 `/tmp/install-deps-<时间戳>.log`。

> 国内网络较慢时，先开代理再跑（脚本会自动探测代理可达性）：
> ```bash
> export https_proxy=http://127.0.0.1:7897 http_proxy=http://127.0.0.1:7897 all_proxy=socks5://127.0.0.1:7897
> ```

装完让环境变量生效：

```bash
source ~/.zshrc        # 让 JAVA_HOME / PATH（openjdk@17、mysql@8.0）生效
```

---

## 三、起 / 停 / 看中间件（日常）

```bash
./scripts/deps-start.sh     # 一键启动全部中间件，并等到就绪才返回
./scripts/deps-status.sh    # 查看每个组件运行状态 + 端口监听
./scripts/deps-stop.sh      # 一键停止全部中间件
```

`deps-start.sh` 会按依赖顺序分阶段启动：ES → ES 索引初始化 → Nacos(API ready) →
RocketMQ（**先 namesrv+broker，等 broker healthy 再起 proxy**）→ LoongCollector。
正常输出末尾应看到 10 个组件全部 `OK`。

---

## 四、编译后端

```bash
# 在 spring-ai-alibaba-admin/ 下
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./mvnw clean package -DskipTests
```

成功标志：`BUILD SUCCESS`，产物 `spring-ai-alibaba-admin-server-start/target/spring-ai-alibaba-admin-server-start.jar`（约 426 MB）。

> 首次编译会下载较多依赖，可能耗时 10+ 分钟。

---

## 五、启动后端应用

```bash
java -jar spring-ai-alibaba-admin-server-start/target/spring-ai-alibaba-admin-server-start.jar \
  --spring.profiles.active=local
```

`local` profile 已内置所有本地连接（MySQL/Redis/ES/Nacos/RocketMQ/OTLP）默认值，无需额外配置。
若要用大模型能力，按需设置 `DASHSCOPE_API_KEY` / `OPENAI_API_KEY` / `DEEPSEEK_API_KEY`。

成功标志：日志出现 `Started SaaStudioAdmin in xx seconds`、`Tomcat started on port 8080`。

**访问地址**：

| 用途 | 地址 |
|---|---|
| 管理界面（生产构建版前端） | http://localhost:8080/ |
| 健康检查 / Actuator | http://localhost:8080/actuator/health |
| API 文档（springdoc） | http://localhost:8080/swagger-ui/index.html |
| Nacos 控制台 | http://localhost:7080/ |
| Kibana | http://localhost:5601/ |

> 默认登录账号：`saa` / `123456`。

停止应用：

```bash
pkill -f spring-ai-alibaba-admin-server-start.jar
```

---

## 六、（可选）启动前端开发服务器

后端首页已内置一份打包好的前端；若要做前端开发（热更新），单独起 umi dev：

```bash
cd frontend
npm install --ignore-scripts     # 先装依赖（跳过会失败的 postinstall，见踩坑 #6）
npm run build:flow               # 构建 spark-flow（main 通过 alias 引用它的 dist，必须先建）
cd packages/main && npm run dev  # umi dev，监听 http://localhost:8000
```

前端 dev 默认端口 **8000**，已配置把 `/api`、`/console`、`/oauth2` 代理到后端 `http://127.0.0.1:8080`。
登录账号同样是 `saa` / `123456`（见 `frontend/packages/main/.env`）。

---

## 七、常见踩坑

| # | 现象 | 原因 | 解决 |
|---|---|---|---|
| 1 | `java`/`mysql` 装了却找不到命令 | openjdk@17、mysql@8.0 是 brew keg-only，不自动进 PATH | `source ~/.zshrc`（install 脚本已把 PATH/JAVA_HOME 写入） |
| 2 | brew install / docker pull 卡住 | 国际源慢 | 先 `export http_proxy/https_proxy/all_proxy`，再跑脚本 |
| 3 | RocketMQ 容器反复重启、`Permission denied` | 宿主 UID/GID 读不到镜像内 750 权限的 `bin/*.sh` | 脚本已生成 `.env`(UID=3000/GID=3000) + macOS override 固定 `user: 3000:3000` |
| 4 | Elasticsearch 退出码 137 / Kibana JS heap OOM | Docker Desktop 内存不足 | 脚本 override 已把 ES 堆降到 512m、Kibana `--max-old-space-size=768`；仍 OOM 就调大 Docker 内存 |
| 5 | 编译报「枚举构造器/getter 缺失」(`需要:没有参数 找到:int,String`) | `ToolExample.java` 曾被教程代码覆盖，parse 报错打断 Lombok 注解处理（**不是 lombok 版本问题**） | 已修复入库（恢复领域类）；如再遇到，去掉 `-q` 看完整日志定位最早的根错误文件 |
| 6 | `npm install` 在 `umi setup` 阶段报 `Can't resolve .../spark-flow/dist` | 先有鸡还是先有蛋：main 的 postinstall 解析 alias 时 spark-flow 还没构建 | 用 `npm install --ignore-scripts`，再 `npm run build:flow`，最后才 `umi dev` |
| 7 | 应用启动失败：`ProducerImpl-0 [FAILED]` / `DEADLINE_EXCEEDED` | ① RocketMQ 5.x 客户端走 gRPC，端点要用 **18081**（不是 remoting 的 18080）；② proxy 早于 broker 就绪启动会降级 | ① `application-local.yml` 已用 `localhost:18081`；② 用 `deps-start.sh` 启动（已保证 broker healthy 后再起 proxy）；手动起过的可 `docker compose -f docker/middleware/docker-compose-prod.yaml restart rmq_proxy` |
| 8 | `GET /api/observability/traces` 返回 400 | `startTime`/`endTime` 为必填 | 传入时间范围（epoch 毫秒），如 `?startTime=...&endTime=...` |

---

## 八、验证清单

### 中间件（任意时刻）
```bash
./scripts/deps-status.sh    # 期望 10 个组件全 OK
```
逐项手动核对：

- [ ] MySQL：`mysqladmin -h127.0.0.1 -P3306 -uadmin -padmin ping` → `mysqld is alive`
- [ ] MySQL 建表：`mysql -h127.0.0.1 -uadmin -padmin admin -e "SHOW TABLES;"` → 27 张表（account/prompt/dataset/evaluator/...）
- [ ] Redis：`redis-cli -p 6379 ping` → `PONG`
- [ ] Elasticsearch：`curl -s localhost:9200/_cluster/health` → `status: green|yellow`
- [ ] ES 索引：`curl -s localhost:9200/loongsuite_traces/_mapping` → 200
- [ ] Kibana：`curl -so /dev/null -w "%{http_code}" localhost:5601` → 200/302
- [ ] Nacos：`curl -so /dev/null -w "%{http_code}" localhost:7080/` → 200/302
- [ ] RocketMQ topic：`docker logs rmq-init-topic | grep topic_saa_studio_document_index`
- [ ] LoongCollector：`nc -z localhost 4318`

### 后端应用
- [ ] 启动日志出现 `Started SaaStudioAdmin` + `Tomcat started on port 8080`
- [ ] `curl -s localhost:8080/actuator/health` → `{"status":"UP"}`（db/elasticsearch/redis 均 UP）
- [ ] `curl -so /dev/null -w "%{http_code}" localhost:8080/` → 200（首页）

### 接口冒烟（带登录态）
```bash
# 1) 登录拿 token
TOKEN=$(curl -s -X POST localhost:8080/console/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"saa","password":"123456"}' | node -e "process.stdin.on('data',d=>console.log(JSON.parse(d).data.access_token))")
# 2) 各模块列表接口（期望 200）
curl -s -o /dev/null -w "prompt %{http_code}\n"    -H "Authorization: Bearer $TOKEN" "localhost:8080/api/prompts?current=1&pageSize=10"
curl -s -o /dev/null -w "dataset %{http_code}\n"   -H "Authorization: Bearer $TOKEN" "localhost:8080/api/dataset/datasets?current=1&pageSize=10"
curl -s -o /dev/null -w "evaluator %{http_code}\n" -H "Authorization: Bearer $TOKEN" "localhost:8080/api/evaluator/evaluators?current=1&pageSize=10"
```
更完整的冒烟结果见 `docs/smoke-test-result.md`。

### 前端（如启动了 dev）
- [ ] `curl -so /dev/null -w "%{http_code}" localhost:8000/` → 200
- [ ] 浏览器打开 http://localhost:8000/ 能跳到登录页，用 `saa/123456` 登录

---

## 附：相关文档

- `scripts/install-deps-log.md` — 中间件安装报告（自动生成）
- `docs/startup-log.md` — 编译/启动全过程与排障记录
- `docs/smoke-test-result.md` — 核心接口冒烟测试结果
- `docs/env-checklist.md` — 依赖与端口清单
- `docs/api-list.md` — REST 接口清单
- `docker-compose.dev.yml`（项目根） — 纯 Docker 版（把应用也容器化的替代方案）
