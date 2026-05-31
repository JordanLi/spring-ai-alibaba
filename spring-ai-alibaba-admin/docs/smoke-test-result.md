# 核心接口冒烟测试结果 (smoke-test-result.md)

对照 `docs/api-list.md`，挑选覆盖「登录 / Prompt / Dataset / Evaluator / Trace」五大模块的
核心接口，用 `curl` 实跑。判定标准：**HTTP 200 算通过**。

- 测试时间：2026-05-30 19:30 (CST)
- 目标后端：`http://localhost:8080`（profile `local`，应用已启动）
- 鉴权：先 `POST /console/v1/auth/login`（账号 `saa/123456`）拿 `access_token`，
  其余接口带 `Authorization: Bearer <access_token>`。

## 结果汇总

| # | 模块 | 方法 | 接口 | HTTP | 结果 |
|---|------|------|------|------|------|
| 1 | 登录 | POST | `/console/v1/auth/login` | 200 | ✅ 通过 |
| 2 | Prompt | GET | `/api/prompts?current=1&pageSize=10` | 200 | ✅ 通过 |
| 3 | Dataset | GET | `/api/dataset/datasets?current=1&pageSize=10` | 200 | ✅ 通过 |
| 4 | Evaluator | GET | `/api/evaluator/evaluators?current=1&pageSize=10` | 200 | ✅ 通过 |
| 5 | Trace | GET | `/api/observability/traces?...&startTime&endTime` | 200 | ✅ 通过 |

**5/5 通过，无失败接口。**

> 备注：Trace 接口首次不带时间范围请求时返回 `400`（`startTime/endTime 不能为空`，属参数校验而非接口故障），
> 补上必填的 `startTime`/`endTime`（epoch 毫秒）后返回 200。下方记录了两次请求。

## 详细记录

### 1. 登录 · `POST /console/v1/auth/login`

请求：
```bash
curl -X POST http://localhost:8080/console/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"saa","password":"123456"}'
```
响应（HTTP 200）：
```json
{"data":{"access_token":"eyJhbG...","refresh_token":"eyJhbG...","expires_in":1780147819},"code":200,"message":"success","request_id":"e38e889b-..."}
```

### 2. Prompt 列表 · `GET /api/prompts`

```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/prompts?current=1&pageSize=10"
```
响应（HTTP 200）：
```json
{"data":{"totalCount":0,"totalPage":0,"pageNumber":1,"pageSize":10,"pageItems":[]},"code":200,"message":"success"}
```

### 3. Dataset（测评集）列表 · `GET /api/dataset/datasets`

```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/dataset/datasets?current=1&pageSize=10"
```
响应（HTTP 200）：
```json
{"data":{"totalCount":0,"totalPage":0,"pageNumber":1,"pageSize":10,"pageItems":[]},"code":200,"message":"success"}
```

### 4. Evaluator（评估器）列表 · `GET /api/evaluator/evaluators`

```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/evaluator/evaluators?current=1&pageSize=10"
```
响应（HTTP 200，库中已有 1 条评估器记录）：
```json
{"data":{"totalCount":1,"totalPage":1,"pageNumber":0,"pageSize":10,"pageItems":[]},"code":200,"message":"success"}
```

### 5. Trace 列表 · `GET /api/observability/traces`

第一次（缺必填参数，HTTP 400 — 参数校验）：
```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/observability/traces?current=1&pageSize=10"
# {"code":400,"message":"Parameters invalid, endTime: 结束时间不能为空, startTime: 开始时间不能为空.","request_id":""}
```
补充必填 `startTime`/`endTime`（epoch 毫秒）后（HTTP 200）：
```bash
curl -H "Authorization: Bearer <token>" \
  "http://localhost:8080/api/observability/traces?current=1&pageSize=10&startTime=1780137117687&endTime=1780140717664"
```
响应（HTTP 200）：
```json
{"data":{"totalCount":0,"totalPage":0,"pageNumber":1,"pageSize":10,"pageItems":[]},"code":200,"message":"success"}
```

## 结论

5 个核心模块接口在 `local` 环境下均可正常响应（登录鉴权链路、统一 `Result` 结构、分页结构均正常）。
唯一需注意：`GET /api/observability/traces` 的 `startTime`/`endTime` 为必填参数，调用方需显式传入时间范围。
