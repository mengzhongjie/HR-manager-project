# API 文档

> 基础路径：后端运行在 `localhost:8080`，前端 Vite 代理 `/api` → `:8080`
> 统一响应格式：`{ code: 0, message: "success", data: {...} }`
> 错误时 `code !== 0`，`message` 包含错误描述

---

## 目录

- [HR 管理端 API](#hr-管理端-api-apihr)
  - [仪表盘](#仪表盘)
  - [候选人管理](#候选人管理)
  - [面试管理](#面试管理)
  - [Offer 管理](#offer-管理)
  - [入职管理](#入职管理)
  - [备选列表](#备选列表)
  - [岗位管理（CRUD）](#岗位管理crud)
- [求职端 API](#求职端-api-api)
- [SSE 端点](#sse-端点)

---

## 约定

### 统一响应格式

```json
{
  "code": 0,        // 0=成功，非0=失败
  "message": "success",
  "data": { ... }   // 实际数据
}
```

### 候选人状态枚举

```
NEW → 新候选人
PENDING_ARCHIVE → 存档待定
INTERVIEW_INVITED → 面试邀约
IN_INTERVIEW → 面试中
ROUND_1_PASSED → 一面通过
ROUND_2_PASSED → 二面通过
WAITING_OFFER → 待发Offer
OFFERED → 已发Offer
ONBOARDED → 已入职
REJECTED → 已淘汰
```

### 面试状态枚举

```
PENDING → 待响应
ACCEPTED → 待面试
IN_PROGRESS → 面试中
COMPLETED → 已完成
```

### 面试结果枚举

```
PASSED → 通过
FAILED → 未通过
```

### 面试轮次枚举

```
ROUND_1 → 一面
ROUND_2 → 二面
ROUND_3 → 三面
```

---

## HR 管理端 API (`/api/hr`)

### 仪表盘

#### GET /api/hr/positions

获取所有岗位名称列表。

**参数**: 无

**响应示例**:
```json
{
  "code": 0,
  "data": ["Java后端工程师", "前端工程师", "产品经理"]
}
```

---

#### GET /api/hr/positions/stats

获取各岗位候选人统计（人数 + 各状态分布）。

**参数**: 无

**响应**:
```json
{
  "data": {
    "positions": ["Java后端工程师", "前端工程师"],
    "counts": { "Java后端工程师": 4, "前端工程师": 2 },
    "statusCounts": {
      "Java后端工程师": { "NEW": 2, "IN_INTERVIEW": 1, "PENDING_ARCHIVE": 1 },
      "前端工程师": { "NEW": 1, "INTERVIEW_INVITED": 1 }
    }
  }
}
```

---

### 候选人管理

#### GET /api/hr/positions/{position}/candidates

按岗位获取候选人列表（含筛选）。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| position | String | 岗位名称 |

**查询参数**（可选，绑定 `CandidateFilterDTO`）:
| 参数 | 类型 | 说明 |
|------|------|------|
| status | String | 状态筛选 |
| keyword | String | 关键词搜索（姓名/邮箱） |
| minAge | Integer | 最小年龄 |
| maxAge | Integer | 最大年龄 |
| educationLevel | String | 学历筛选 |
| minExperience | Integer | 最低工作年限 |
| maxExperience | Integer | 最高工作年限 |

**响应**: `List<Candidate>`

---

#### GET /api/hr/candidates/{id}

获取候选人详情。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 候选人 ID |

**响应**: `Candidate`（不存在返回 404）

**Candidate 数据结构**:
```json
{
  "id": "string",
  "seekerId": "string",
  "name": "张三",
  "age": 25,
  "email": "zhangsan@email.com",
  "phone": "13800138001",
  "position": "Java后端工程师",
  "yearsOfExperience": 5,
  "isFreshGraduate": false,
  "graduationYear": 2020,
  "educationLevel": "BACHELOR",
  "school": "华中科技大学",
  "major": "计算机科学与技术",
  "techStack": ["Java", "Spring", "MySQL"],
  "workHistory": "工作经历摘要",
  "selfEvaluation": "自我评价",
  "resumeFileName": "原始文件名.pdf",
  "resumeFilePath": "uuid.pdf",
  "status": "IN_INTERVIEW",
  "interviewRound": 2,
  "aiQualification": { "score": 85, "recommendation": "INTERVIEW_INVITED" }
}
```

---

#### PUT /api/hr/candidates/{id}/status

更新候选人状态（受状态机约束，非法转换会拒绝）。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 候选人 ID |

**请求参数**:
| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| status | CandidateStatus | 必填 | 目标状态 |
| actor | String | "HR" | 操作人标识 |
| reason | String | "" | 变更原因（空则自动填充） |

---

#### POST /api/hr/candidates/{id}/ai-qualify

触发 AI 资质评定。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 候选人 ID |

**响应**: `"AI资质评定完成"`

---

#### POST /api/hr/candidates/{id}/invite-interview

发送面试邀请。自动检测下一轮次（根据已有面试记录），创建面试记录并设置候选人状态为 `INTERVIEW_INVITED`。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 候选人 ID |

**请求参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| interviewDate | String (可选) | 预约面试日期，格式 yyyy-MM-dd，必须在今天之后 |

**响应**: `InterviewRecord`

---

#### PUT /api/hr/interviews/{id}/start

开始面试（面试官进入，状态设为 `IN_PROGRESS`）。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 面试记录 ID |

**响应**: `InterviewRecord`

---

#### PUT /api/hr/interviews/{id}/complete

结束面试。填写评分/结果/反馈，自动推进候选人状态机。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 面试记录 ID |

**请求参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| result | InterviewResult | 面试结果（PASSED/FAILED） |
| score | Integer (可选) | 评分 |
| feedback | String (可选) | 面试反馈 |

**状态自动推进**:
```
PASSED → ROUND_1_PASSED / ROUND_2_PASSED / WAITING_OFFER
FAILED → REJECTED
```

---

### 面试管理

#### GET /api/hr/interviews

获取所有面试记录的概览列表。

---

#### GET /api/hr/candidates/{candidateId}/interviews

获取指定候选人的所有面试记录（按轮次升序）。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| candidateId | String | 候选人 ID |

**响应**: `List<InterviewRecord>`

**InterviewRecord 数据结构**:
```json
{
  "id": "string",
  "candidateId": "string",
  "candidateName": "张三",
  "candidatePosition": "Java后端工程师",
  "round": "ROUND_1",
  "interviewStatus": "COMPLETED",
  "result": "PASSED",
  "score": 85,
  "feedback": "技术基础扎实",
  "interviewerName": "王面试官",
  "interviewDate": "2026-06-22"
}
```

---

#### POST /api/hr/interviews

手动创建面试记录（通常由 invite-interview 自动创建）。

**请求体**: `InterviewRecord` JSON

---

### Offer 管理

#### GET /api/hr/offers/pending

获取待发 Offer 列表（`WAITING_OFFER` 状态候选人）。

---

#### POST /api/hr/offers

创建 Offer。

**请求参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| candidateId | String | 候选人 ID |

**请求体**: Offer JSON

---

#### GET /api/hr/offers

获取所有 Offer 列表。

---

#### GET /api/hr/candidates/{candidateId}/offer

获取指定候选人的 Offer（无 Offer 时 data 为 null）。

---

### 入职管理

#### POST /api/hr/onboarding

创建入职登记。

**请求参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| offerId | String | Offer ID |

**请求体**: Onboarding JSON

---

#### GET /api/hr/candidates/{candidateId}/onboarding

获取指定候选人的入职信息（不存在时 data 为 null）。

---

### 备选列表

#### GET /api/hr/positions/{position}/backup

获取指定岗位的备选候选人列表（`PENDING_ARCHIVE` 状态）。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| position | String | 岗位名称 |

---

#### PUT /api/hr/candidates/{id}/restore

从备选库恢复候选人（状态设为 `NEW`）。

---

### 岗位管理（CRUD）

#### GET /api/hr/positions/list

获取所有岗位列表。使用 Redis 旁路缓存（TTL 300 秒），写操作后自动清除。

---

#### POST /api/hr/positions

创建新岗位。校验名称非空和唯一性，发布 `PositionChangeEvent`。

**请求体**: Position JSON
```json
{
  "name": "Java后端工程师",
  "description": "负责后端系统开发",
  "department": "技术部",
  "requirements": "熟悉 Spring Boot"
}
```

---

#### PUT /api/hr/positions/{id}

更新岗位信息。发布 `PositionChangeEvent`。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 岗位 ID |

**请求体**: 要更新的字段（部分更新）

---

#### DELETE /api/hr/positions/{id}

删除岗位。有关联候选人时拒绝删除。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 岗位 ID |

---

## 求职端 API (`/api`)

#### POST /api/seeker/demo

获取或创建演示求职者（无登录认证）。系统使用匿名演示账号模式。

**参数**: 无

**响应**:
```json
{
  "data": {
    "id": "6a3f...",
    "username": "demo",
    "name": "演示用户",
    "email": "demo@example.com",
    "phone": "13800138000"
  }
}
```

---

#### GET /api/seeker/{id}/status

获取求职者完整状态（所有岗位的投递列表）。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 求职者 ID |

**响应**:
```json
{
  "data": {
    "seeker": { ... },
    "candidates": [
      {
        "candidate": { ... },
        "interviews": [ ... ],
        "offer": { ... } | null
      }
    ]
  }
}
```

---

#### GET /api/seeker/{id}/can-submit

检查能否投递指定岗位。每个岗位只能有一个进行中的申请。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 求职者 ID |

**查询参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| position | String (可选) | 岗位名称，不传则默认可投递 |

**响应**:
```json
{
  "data": { "canSubmit": true, "seekerId": "..." }
}
```

---

#### PUT /api/interview/{id}/respond

求职者响应面试邀约。接受则进入 `IN_INTERVIEW`，拒绝则进入 `REJECTED`。异步通知 HR。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | 面试记录 ID |

**请求参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| accept | boolean | true=接受, false=拒绝 |

---

#### POST /api/resume/parse-sync

同步简历解析。上传 PDF → 保存 → PDFBox 提取文本（空则降级 OCR）→ AI 解析 → 返回结构化 JSON。

**请求体**: multipart/form-data
| 字段 | 类型 | 说明 |
|------|------|------|
| file | File | PDF 简历文件（最大 10MB） |
| seekerId | String | 求职者 ID |
| position | String | 应聘岗位 |

**响应**:
```json
{
  "data": {
    "storedFileName": "uuid.pdf",
    "resumeFileName": "原始文件名.pdf",
    "candidate": { ... }  // 解析后的 Candidate 对象
  }
}
```

---

#### POST /api/resume/submit

提交完整的候选人信息到 MongoDB。含三级降级：RabbitMQ → Redis List → 直接 MongoDB。

**请求体**: JSON
```json
{
  "seekerId": "string",
  "storedFileName": "uuid.pdf",
  "resumeFileName": "原始文件名.pdf",
  "candidate": { ... }
}
```

**响应**: `{ "data": { "status": "submitted", "position": "..." } }`

---

#### PUT /api/seeker/candidate/{candidateId}

求职者更新/补充自己的候选人基本信息（不允许变更状态）。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| candidateId | String | 候选人 ID |

**请求体**: Candidate JSON（部分字段更新：name, email, phone, position, school, major, techStack 等）

---

#### PUT /api/offer/{id}/respond

求职者响应 Offer。

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | String | Offer ID |

**请求参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| accept | boolean | true=接受, false=拒绝 |

---

## SSE 端点

#### GET /api/hr/positions/events

SSE（Server-Sent Events）订阅端点。EventSource 连接后接收实时事件推送。

**事件类型**:
| 事件名 | 数据格式 | 触发时机 |
|--------|----------|----------|
| `connected` | 纯文本 | 连接成功 |
| `position-change` | `PositionChangeEvent` JSON | 岗位增删改 |
| `interview-status` | `InterviewStatusEvent` JSON | 面试状态变更 |

**Nginx 代理配置注意事项**:
```nginx
location /api/hr/positions/events {
    proxy_pass http://hr_backend;
    proxy_http_version 1.1;
    proxy_set_header Connection '';
    proxy_buffering off;     # 必须关闭缓冲
    proxy_cache off;         # 关闭缓存
    proxy_read_timeout 86400s;  # 长超时
}
```
