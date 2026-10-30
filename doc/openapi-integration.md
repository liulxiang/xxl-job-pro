# XXL-JOB-Pro 开放 API（OpenAPI）对接指南

面向第三方系统 / 异构项目的 **机器对机器（M2M）** 接口，用于远程 **创建 / 更新 / 启停 / 删除 / 查询任务**、**触发执行** 与 **回查执行结果**。与内部 `/api`（调度中心↔执行器私有协议）及后台 `/jobinfo`（Cookie 会话）在路径与鉴权上完全物理隔离。

- 统一前缀：`{context-path}/openapi/v1`，默认 `context-path=/xxl-job-admin`
- 交互方式：**全部使用 `POST`**，请求体为 `application/json;charset=UTF-8`
- 鉴权方式：**AppKey + AppSecret + HMAC-SHA256 签名 + 时间戳 + nonce 防重放**
- 内置文档：调度中心登录后进入「使用教程」页，含接口清单、字段说明、Webhook 规范与**在线签名调试器**（可生成签名头与 cURL）

---

## 1. 快速开始（三步）

1. **建表 / 升级表**：确保库中存在 `xxl_job_api_client`、`xxl_job_openapi_callback`（见 `doc/db/tables_xxl_job.sql`）。
2. **创建凭证**：登录调度中心后台 →「开放API凭证」页面（仅管理员可见），新建一条凭证：填写 `AppKey`、`AppSecret`、绑定的后台账号 `username`、（可选）默认回调 URL `callback_url`、状态启用。
3. **签名调用**：按第 3 节算法生成签名头，调用第 4 节端点。集成方可直接依赖 `xxl-job-core` 使用内置 SDK（第 6 节），免手写签名。

> 权限模型：凭证绑定的 `username` 决定可操作范围。管理员账号可操作全部执行器组；普通账号仅能操作其权限字段（执行器组 ID 列表）内的任务。越权将返回权限错误。

---

## 2. 凭证与表结构

### 2.1 `xxl_job_api_client`（接入凭证）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | int | 主键 |
| `app_key` | varchar(64) | AppKey，唯一 |
| `app_secret` | varchar(128) | AppSecret，HMAC-SHA256 签名密钥（后台列表页脱敏显示） |
| `username` | varchar(64) | 绑定的 `xxl_job_user` 账号，用于权限校验 |
| `callback_url` | varchar(255) | Webhook 结果回调默认 URL，触发时可按次覆盖 |
| `status` | tinyint | 0-禁用、1-启用 |
| `remark` | varchar(255) | 备注 |
| `add_time` / `update_time` | datetime | 时间戳 |

### 2.2 `xxl_job_openapi_callback`（回调登记，结果回推用）

| 字段 | 类型 | 说明 |
|------|------|------|
| `log_id` | bigint | 触发产生的调度日志 ID，唯一（去重键） |
| `app_key` | varchar(64) | 发起触发的 AppKey |
| `webhook_url` | varchar(255) | 结果回调 URL |
| `status` | tinyint | 0-待回调、1-已回调、2-已放弃 |
| `retry_count` | int | 已重试次数 |
| `next_retry_time` | datetime | 下次重试/补偿时间 |
| `add_time` | datetime | 登记时间 |

---

## 3. 签名算法（核心）

### 3.1 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `XXL-JOB-APP-KEY` | 是 | 分配的 AppKey |
| `XXL-JOB-TIMESTAMP` | 是 | 毫秒时间戳，须在 ±窗口内（默认 5 分钟，见配置） |
| `XXL-JOB-NONCE` | 是 | 随机串（如 UUID 去横线），窗口内不可重复 |
| `XXL-JOB-SIGN` | 是 | 下方签名值 |

### 3.2 待签名字符串 stringToSign

以 **换行符 `\n`** 拼接四段（无 body 时 `rawBody` 取空串）：

```
stringToSign = appKey + "\n" + timestamp + "\n" + nonce + "\n" + rawBody
```

- `rawBody`：请求体的 **原始字符串**（与实际发送的字节完全一致；无 body 传 `""`）。
- `timestamp` / `nonce`：与请求头中取值 **完全一致**。

### 3.3 签名值

```
sign = lowercaseHex( HmacSHA256( key = appSecret, data = stringToSign ) )
```

输出为 **小写十六进制**（64 字符）。

### 3.4 服务端校验顺序

1. 四个请求头是否齐全；
2. `timestamp` 是否在窗口内（防重放第一道）；
3. 按 `app_key` 加载 **启用状态** 的凭证，不存在/禁用即拒；
4. 用凭证 `app_secret` 重算签名并 **常量时间比对**（防时序攻击）；
5. `nonce` 是否已在窗口内出现过（内存缓存，防重放第二道）；
6. 绑定 `username` 是否存在，通过后写入请求上下文供权限模型复用。

任一环节不通过：**HTTP 401**，返回体 `{ "code": 401, "msg": "<原因>", "content": null }`。

---

## 4. 接口清单

所有端点均为 `POST`，URL 形如 `{baseUrl}/openapi/v1/...`。返回体统一为：

```json
{ "code": 200, "msg": "success", "content": <payload> }
```

`code = 200` 表示成功；非 200 时 `msg` 为失败原因。

| 端点 | 请求 | content（成功时） |
|------|------|-------------------|
| `/job/add` | 任务对象 JSON（见 4.1） | 新任务 ID（数字） |
| `/job/update` | 任务对象 JSON（须含 `id`） | null |
| `/job/start?id={jobId}` | 空 body（`""`） | null |
| `/job/stop?id={jobId}` | 空 body（`""`） | null |
| `/job/remove?id={jobId}` | 空 body（`""`） | null |
| `/job/get?id={jobId}` | 空 body（`""`） | 任务对象 |
| `/job/trigger` | 触发请求 JSON（见 4.2） | 本次调度 `logId`（字符串数字） |
| `/joblog/result?logId={logId}` | 空 body（`""`） | 结果对象（见 4.3） |

> ⚠️ 签名对 **完整 rawBody** 计算。`start/stop/remove/get/result` 用 **URL 查询参数** 传 id，且 body 为空串——签名时 `rawBody` 用 `""`。

### 4.1 创建 / 更新任务字段（`/job/add`、`/job/update`）

必填核心字段：

| 字段 | 说明 |
|------|------|
| `jobGroup` | 执行器组 ID（凭证账号须有该组权限） |
| `jobDesc` | 任务描述 |
| `author` | 负责人 |
| `scheduleType` | `CRON` / `FIX_RATE` |
| `scheduleConf` | CRON 表达式，或 FIX_RATE 的秒数字符串（≥1） |
| `glueType` | `BEAN` / `GLUE_SHELL` / `GLUE_PYTHON` / ... |
| `executorHandler` | 当 `glueType=BEAN` 时必填，对应执行器 `@XxlJob("xxx")` |

常用可选字段：`alarmEmail`、`misfireStrategy`（`DO_NOTHING`/`FIRE_ONCE_NOW`）、`executorRouteStrategy`（`FIRST`/`ROUND`/`RANDOM`/`CONSISTENT_HASH`/...）、`executorParam`、`executorBlockStrategy`（`SERIAL_EXECUTION`/`DISCARD_LATER`/`COVER_EARLY`）、`executorTimeout`、`executorFailRetryCount`、`childJobId`。

示例 body：

```json
{
  "jobGroup": 1,
  "jobDesc": "SDK创建的示例任务",
  "author": "openapi-demo",
  "alarmEmail": "",
  "scheduleType": "CRON",
  "scheduleConf": "0 0/5 * * * ?",
  "misfireStrategy": "DO_NOTHING",
  "executorRouteStrategy": "FIRST",
  "executorHandler": "demoJobHandler",
  "executorParam": "hello from openapi",
  "executorBlockStrategy": "SERIAL_EXECUTION",
  "executorTimeout": 0,
  "executorFailRetryCount": 0,
  "glueType": "BEAN"
}
```

### 4.2 触发请求（`/job/trigger`）

```json
{
  "jobId": 5,
  "executorParam": "覆盖任务的执行参数，可为 null",
  "addressList": "指定执行器地址，可为 null（走路由策略）",
  "webhookUrl": "本次结果回调地址，可为 null（回退凭证 callback_url）"
}
```

成功返回 `content` = 本次调度 `logId`。触发为异步执行，如需结果请 **轮询 `/joblog/result`** 或配置 **Webhook 回调**（第 5 节）。

### 4.3 结果对象（`/joblog/result`）

```json
{
  "logId": 123,
  "jobId": 5,
  "triggerCode": 200,
  "triggerMsg": "...",
  "handleCode": 200,
  "handleMsg": "..."
}
```

- `triggerCode`：调度结果码（200 成功）。
- `handleCode`：执行结果码。`0` = 未开始/进行中；`200` = 执行成功；其它 = 执行失败。

---

## 5. Webhook 结果回调

在触发时提供 `webhookUrl`（或凭证配置了默认 `callback_url`），任务进入终态后调度中心会 **主动 POST 执行结果** 到该 URL。

### 5.1 回调请求

- 方法：`POST`，`Content-Type: application/json;charset=UTF-8`
- Body（payload）：

```json
{
  "logId": 123,
  "jobId": 5,
  "jobGroup": 1,
  "executorAddress": "192.168.1.10:9999",
  "triggerCode": 200,
  "handleCode": 200,
  "handleMsg": "执行结果纯文本（已去除 HTML，截断 2000 字）",
  "finishTime": 1730000000000
}
```

- 期望响应：`HTTP 2xx` 视为投递成功；非 2xx 进入补偿重投。

### 5.2 回调来源验签头

回调附带请求头：

```
Xxl-Job-Callback-Sign: lowercaseHex( HmacSHA256( key = appSecret, data = rawBody ) )
```

接收方用 **同一 AppSecret** 对收到的 **原始 body 字节** 重算 HMAC 并比对，一致即确认来源可信。

> 边界：若凭证被禁用/删除导致取不到 AppSecret，回调将 **不携带该头**，接收方应把「无签名」视为不可信并拒绝处理。

### 5.3 可靠性与幂等

- **即时 + 补偿双链路**：完成即尝试投递；失败或未投递成功的记录由后台补偿线程每 30s 扫描重投，退避 2 分钟、最多 5 次，超限置「已放弃」。
- **集群去重**：以 `next_retry_time` 作乐观锁 token 认领，多节点不会重复投递。
- **仍需接收方保证幂等**：验签只证明来源，不防重复；建议按 `logId` 去重。

### 5.4 接收端验签示例（Java）

```java
String got = request.getHeader("Xxl-Job-Callback-Sign");
String rawBody = readRawBody(request);                 // 与实际字节一致
String expect = lowercaseHex(hmacSha256(appSecret, rawBody));
if (got == null || !MessageDigest.isEqual(expect.getBytes(UTF_8), got.getBytes(UTF_8))) {
    response.setStatus(401);                            // 不可信，拒绝
    return;
}
// 校验通过：解析 JSON（logId/handleCode/handleMsg...），按 logId 幂等处理
```

---

## 6. 内置 SDK（Java，推荐）

集成方 **仅需依赖 `xxl-job-core`**，签名/时间戳/nonce 由 SDK 自动处理。

```xml
<dependency>
    <groupId>com.xxl</groupId>
    <artifactId>xxl-job-core</artifactId>
    <version>${xxl-job.version}</version>
</dependency>
```

```java
import com.xxl.job.core.openapi.OpenApiClient;
import com.xxl.job.core.openapi.OpenApiResult;
import java.util.LinkedHashMap;
import java.util.Map;

OpenApiClient client = new OpenApiClient(
        "http://127.0.0.1:8080/xxl-job-admin", "your_app_key", "your_app_secret");

// 创建任务
Map<String, Object> job = new LinkedHashMap<>();
job.put("jobGroup", 1);
job.put("jobDesc", "示例任务");
job.put("author", "sys-a");
job.put("scheduleType", "CRON");
job.put("scheduleConf", "0 0/5 * * * ?");
job.put("glueType", "BEAN");
job.put("executorHandler", "demoJobHandler");
job.put("executorRouteStrategy", "FIRST");
job.put("executorBlockStrategy", "SERIAL_EXECUTION");
OpenApiResult add = client.addJob(job);
int jobId = Integer.parseInt(add.getContentAsString());          // content = 新任务ID

// 启动 / 触发（拿 logId）/ 回查
client.startJob(jobId);
OpenApiResult trig = client.trigger(jobId, "param", null);        // 3 参重载
// 指定本次回调地址：client.trigger(jobId, "param", null, "http://you/cb");  // 4 参重载
long logId = Long.parseLong(trig.getContentAsString());           // content = logId
OpenApiResult result = client.queryResult(logId);                 // content = 结果 Map

// 其它：updateJob(map 含 id) / stopJob / removeJob / getJob
```

SDK 方法一览：

| 方法 | 对应端点 |
|------|----------|
| `addJob(Map)` | `/job/add` |
| `updateJob(Map)` | `/job/update` |
| `startJob(int)` / `stopJob(int)` / `removeJob(int)` / `getJob(int)` | `/job/start` `/job/stop` `/job/remove` `/job/get` |
| `trigger(int jobId, String executorParam, String addressList)` | `/job/trigger` |
| `trigger(int jobId, String executorParam, String addressList, String webhookUrl)` | `/job/trigger`（带回调） |
| `queryResult(long logId)` | `/joblog/result` |

`OpenApiResult`：`code` / `msg` / `content` / `isSuccess()` / `getContentAsString()`。

完整可运行示例见 `xxl-job-executor-sample-springboot` 的 `com.xxl.job.executor.openapi.OpenApiClientDemo`。

---

## 7. 配置项

`application.properties`：

```properties
### xxl-job, openapi（远程API创建/触发任务）
# 时间戳有效窗口（毫秒），默认 5 分钟；同时用于 nonce 防重放缓存 TTL
xxl.job.openapi.timestamp.window=300000
```

---

## 8. 非 Java 语言签名示例（Python）

```python
import hmac, hashlib, time, uuid, json, requests

BASE = "http://127.0.0.1:8080/xxl-job-admin"
APP_KEY, APP_SECRET = "your_app_key", "your_app_secret"

def call(path, body_obj=None, query=""):
    raw = json.dumps(body_obj, ensure_ascii=False, separators=(',', ':')) if body_obj is not None else ""
    ts = str(int(time.time() * 1000))
    nonce = uuid.uuid4().hex
    to_sign = "\n".join([APP_KEY, ts, nonce, raw])
    sign = hmac.new(APP_SECRET.encode(), to_sign.encode(), hashlib.sha256).hexdigest()  # 小写 hex
    headers = {
        "Content-Type": "application/json;charset=UTF-8",
        "XXL-JOB-APP-KEY": APP_KEY, "XXL-JOB-TIMESTAMP": ts,
        "XXL-JOB-NONCE": nonce, "XXL-JOB-SIGN": sign,
    }
    r = requests.post(BASE + "/openapi/v1" + path + query, data=raw.encode("utf-8"), headers=headers)
    return r.json()

print(call("/job/trigger", {"jobId": 5, "executorParam": "hi", "addressList": None}))
```

> 关键点：`rawBody` 必须与 **实际发送字节** 完全一致（注意 JSON 序列化格式、编码），否则签名不匹配。建议直接发送 `raw` 字符串本身（如上 `data=raw.encode()`），而非让 HTTP 库重新序列化。

---

## 9. 错误与限制

| 场景 | 表现 |
|------|------|
| 缺头 / 时间戳过期 / 签名不符 / nonce 重复 / 凭证禁用 | HTTP 401 + `msg` 原因 |
| 越权操作非授权执行器组 | `code != 200`，`msg` 含 `permission limit` |
| 任务/日志不存在 | `code != 200`，`msg` 含 `not found` |
| **集群 nonce 防重放** | 基于 **单机内存缓存**，多节点部署时同一 nonce 在极短窗口内理论上可能被不同节点各放行一次；如需强一致可替换为 Redis。生产建议配合 HTTPS + 时间戳窗口收敛风险 |
| **AppSecret 安全** | 后台列表页脱敏展示，明文仅创建时可见，请妥善保管 |
