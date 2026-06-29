# 流程图文档

> 本文件使用 Mermaid 语法绘制流程图，在 GitHub/GitLab 上可自动渲染。

---

## 1. 架构总览

```mermaid
graph TB
    subgraph 前端[前端 - Vite Dev Server :5173]
        S[求职端 SPA<br/>/seeker/*]
        H[HR 端 SPA<br/>/hr/*]
    end

    subgraph Nginx[Nginx 反向代理 :8088]
        direction LR
        API["/api/ → :8080"]
        FE["/ → :5173"]
        SSE["/api/hr/positions/events<br/>buffering off"]
    end

    subgraph 后端[Spring Boot 后端 :8080]
        C1[SeekerApiController]
        C2[HrApiController]
        C3[PositionSseController]
        SVC[Service 层<br/>CandidateService<br/>InterviewService<br/>...]
        AGENT[Agent 层<br/>ResumeParseAgent<br/>CandidateQualifyAgent]
    end

    subgraph 存储[存储层]
        M[(MongoDB<br/>hrms_db)]
        R[(Redis<br/>缓存/锁/队列)]
        RMQ[RabbitMQ<br/>事件广播/异步落盘]
    end

    S --> Nginx
    H --> Nginx
    Nginx --> 后端
    C1 --> SVC
    C2 --> SVC
    C3 --> SVC
    SVC --> M
    SVC --> R
    SVC --> RMQ
    SVC --> AGENT
```

---

## 2. 简历解析流程

```mermaid
sequenceDiagram
    participant U as 求职者
    participant F as 前端 ApplyView.vue
    participant API as SeekerApiController
    participant PDF as ResumeTextExtractionService
    participant OCR as ScannedPdfOcrService
    participant AI as ResumeParseAgent
    participant DB as MongoDB

    U->>F: 选择岗位 + 上传 PDF
    F->>API: POST /api/resume/parse-sync
    
    alt 重复投递校验
        API->>API: seekerService.canSubmit()
        API-->>F: 400 "该岗位已有进行中的申请"
    end
    
    API->>PDF: extractText(file)
    
    alt 文字版 PDF
        PDF->>PDF: PDFBox stripper.getText()
        PDF-->>API: 返回文字
    else 扫描件 PDF
        PDF->>OCR: ocr(pdfBytes)
        OCR->>OCR: POST multipart PDF → DeepSeek /v1/file/read
        OCR-->>PDF: 返回提取文字
        PDF-->>API: OCR 文字
    else 仍为空
        PDF-->>API: 抛异常 "PDF文件内容为空"
    end

    API->>AI: execute(extractedText)
    
    alt 配置了 API Key (mock=false)
        AI->>AI: callRealAI() → DeepSeek chat API
    else Mock 模式 (mock=true)
        AI->>AI: mockParse() → 正则提取
    end
    
    AI-->>API: Candidate 对象
    API-->>F: { storedFileName, resumeFileName, candidate }

    F->>F: 显示编辑表单<br/>✓/⚠ 标记提取状态
    U->>F: 补充/修正字段
    F->>API: POST /api/resume/submit

    Note over API,DB: 三级降级写入
    alt RabbitMQ 可用
        API->>RMQ: 发送到 hr.resume.queue
        RMQ->>ResumeSubmitConsumer: 批量 10条/5秒 落盘
        ResumeSubmitConsumer->>DB: 批量 insert
    else MQ 不可用
        API->>R: Redis List 降级
        R->>ResumeSubmitFallbackService: 定时消费
        ResumeSubmitFallbackService->>DB: 写入
    else 全部不可用
        API->>DB: 直接 MongoDB 写入
    end
```

---

## 3. 面试生命周期

```mermaid
sequenceDiagram
    participant HR as HR
    participant Api as HrApiController
    participant Svc as InterviewService
    participant DB as MongoDB
    participant Seeker as 求职者
    participant Notify as InterviewNotificationService

    HR->>Api: POST /candidates/{id}/invite-interview
    Api->>Api: 自动检测下一轮次（R1/R2/R3）
    Api->>Api: 校验面试日期 > today
    Api->>DB: 创建 InterviewRecord<br/>(interviewStatus=PENDING)
    Api->>Svc: updateStatus → INTERVIEW_INVITED
    Api-->>HR: 成功

    Note over Seeker: 求职端 StatusView 显示"接受面试"按钮

    Seeker->>Api: PUT /interview/{id}/respond?accept=true
    Api->>Api: 找到待响应的面试记录<br/>(interviewStatus=PENDING)
    Api->>DB: interviewStatus = ACCEPTED
    Api->>Svc: updateStatus → IN_INTERVIEW
    Api->>Notify: 发布 InterviewStatusEvent
    Notify-->>HR: SSE 实时推送面试状态变更

    HR->>Api: PUT /hr/interviews/{id}/start
    Api->>DB: interviewStatus = IN_PROGRESS
    Api-->>HR: 成功

    HR->>Api: PUT /hr/interviews/{id}/complete<br/>?result=PASSED&score=85
    Api->>DB: interviewStatus = COMPLETED<br/>result = PASSED<br/>score = 85
    Api->>Svc: saveInterview(interview)
    Svc->>Svc: 校验轮次顺序
    Svc->>Svc: 推进候选人状态<br/>PASSED→下一轮/WAITING_OFFER
    Api-->>HR: 成功
```

### 面试记录生命周期

```mermaid
stateDiagram-v2
    [*] --> PENDING: HR 发送邀约
    PENDING --> ACCEPTED: 求职者接受
    PENDING --> [*]: 求职者拒绝<br/>(候选人→REJECTED)
    ACCEPTED --> IN_PROGRESS: HR 开始面试
    IN_PROGRESS --> COMPLETED: HR 结束面试
    
    state COMPLETED {
        [*] --> 通过
        [*] --> 未通过
    }
    
    通过 --> 候选人状态推进: PASSED
    未通过 --> 候选人→REJECTED: FAILED
```

---

## 4. 候选人状态机

```mermaid
stateDiagram-v2
    [*] --> NEW: 简历投递

    NEW --> PENDING_ARCHIVE: 存入备选库
    NEW --> REJECTED: 直接淘汰
    NEW --> INTERVIEW_INVITED: HR发送面试邀约

    INTERVIEW_INVITED --> IN_INTERVIEW: 求职者接受
    INTERVIEW_INvITED --> REJECTED: 求职者拒绝

    IN_INTERVIEW --> ROUND_1_PASSED: 一面通过
    IN_INTERVIEW --> REJECTED: 一面未通过

    ROUND_1_PASSED --> INTERVIEW_INVITED: HR发送二面邀约
    ROUND_1_PASSED --> PENDING_ARCHIVE: 暂存入备选

    ROUND_2_PASSED --> INTERVIEW_INVITED: HR发送三面邀约
    ROUND_2_PASSED --> PENDING_ARCHIVE: 暂存入备选

    ROUND_1_PASSED --> REJECTED: 二面未通过
    ROUND_2_PASSED --> REJECTED: 三面未通过

    WAITING_OFFER --> OFFERED: HR发放Offer
    WAITING_OFFER --> PENDING_ARCHIVE: 暂不发放

    OFFERED --> ONBOARDED: 求职者接受Offer
    OFFERED --> REJECTED: 求职者拒绝Offer
    
    PENDING_ARCHIVE --> NEW: 从备选恢复

    note right of INTERVIEW_INVITED
        自动检测下一轮次
        R1→R2→R3
    end note
```

### 状态流转规则

| 当前状态 | 允许的下一个状态 | 说明 |
|----------|-----------------|------|
| NEW | PENDING_ARCHIVE, REJECTED, INTERVIEW_INVITED | 初始状态 |
| PENDING_ARCHIVE | NEW | 双向可逆 |
| INTERVIEW_INVITED | IN_INTERVIEW, REJECTED | 由求职者决定 |
| IN_INTERVIEW | ROUND_1_PASSED, REJECTED | 一面结果决定 |
| ROUND_1_PASSED | INTERVIEW_INVITED, PENDING_ARCHIVE, REJECTED | 等待二面 |
| ROUND_2_PASSED | INTERVIEW_INVITED, PENDING_ARCHIVE, REJECTED | 等待三面 |
| WAITING_OFFER | OFFERED, PENDING_ARCHIVE | 三面通过 |
| OFFERED | ONBOARDED, REJECTED | 由求职者决定 |
| ONBOARDED | — | 终态 |
| REJECTED | — | 终态 |

---

## 5. 岗位实时同步（SSE）

```mermaid
sequenceDiagram
    participant HR as HR 端
    participant Api as HrApiController
    participant Event as PositionEventService
    participant Redis as Redis
    participant RMQ as RabbitMQ
    participant SSE as PositionSseService
    participant Browser as 求职端/HR端 浏览器

    HR->>Api: 增/删/改 岗位
    
    Api->>Api: 保存到 MongoDB
    Api->>Event: publish PositionChangeEvent

    Event->>Redis: evictPositionCache()<br/>清除 "cache:positions"
    Event->>RMQ: 发布到 position 交换机

    RMQ->>SSE: @RabbitListener 接收事件
    SSE->>SSE: broadcast(eventJson)
    SSE-->>Browser: SSE "position-change" 事件

    Browser->>Browser: 自动刷新岗位列表<br/>(UploadView / PositionManageView)

    Note over Browser: EventSource 自动断线重连
```

### Redis 旁路缓存

```mermaid
flowchart LR
    A[GET /api/hr/positions/list] --> B{Redis 有缓存?}
    B -->|命中| C[返回缓存数据<br/>并刷新TTL]
    B -->|未命中| D[MongoDB findAll]
    D --> E[SETEX 300秒<br/>写入Redis]
    E --> F[返回数据]
    C --> G[返回]
    F --> G
```

---

## 6. 三级降级写入

### 简历提交降级

```mermaid
flowchart TD
    A[POST /api/resume/submit] --> B[RabbitMQ 一级]
    B -->|可用| C[发送到 hr.resume.queue]
    C --> D[ResumeSubmitConsumer<br/>10条/5秒 批量落盘]
    D --> E[(MongoDB)]
    
    B -->|不可用| F[Redis List 二级]
    F -->|可用| G[LPUSH 到 resume:fallback:list]
    G --> H[定时消费<br/>ResumeSubmitFallbackService]
    H --> E
    
    F -->|不可用| I[直接 MongoDB 三级]
    I --> E
```

### 面试通知降级

```mermaid
flowchart TD
    A[面试状态变更<br/>InterviewStatusEvent] --> B[RabbitMQ 一级]
    B -->|可用| C[发送到 hr.interview.queue]
    C --> D[PositionSseService 消费]
    D --> E[SSE 广播给 HR 端]
    
    B -->|不可用| F[Redis List 二级]
    F -->|可用| G[RPUSH 到 interview:notification:list]
    G --> H[定时消费]
    H --> E
    
    F -->|不可用| I[直接 SSE 广播 三级]
    I --> E
```

---

## 7. 分布式锁机制

```mermaid
sequenceDiagram
    participant Client as 请求A
    participant Client2 as 请求B
    participant Redis as Redis
    participant DB as MongoDB

    Client->>Redis: SETNX state_lock:candidate:{id}<br/>(UUID, 30秒TTL)
    Redis-->>Client: OK (拿到锁)

    Client2->>Redis: SETNX state_lock:candidate:{id}<br/>(UUID, 30秒TTL)
    Redis-->>Client2: FAIL (被A持有)

    par 持有锁的线程
        Client->>Client: 状态机校验 validateStatusTransition()
        Client->>Client: 记录 Event Sourcing statusHistory[]
        Client->>DB: MongoDB save()<br/>@Version 乐观锁校验
    end

    par 自旋重试
        Client2->>Client2: 等待 100ms
        Client2->>Redis: SETNX (重试)
        Note over Client2: 最多等待 3 秒
        Client2->>Client2: 超时则抛"系统繁忙"
    end

    Client->>Redis: EVAL Lua脚本<br/>if get(key)==UUID then del(key)
    Note over Client,Redis: 原子操作，防误删
```

### 三层一致性保障

| 层级 | 技术 | 作用 |
|------|------|------|
| 第 1 层 | MongoDB @Version 乐观锁 | 防止并发写覆盖 |
| 第 2 层 | Event Sourcing statusHistory[] | 每次状态变更生成审计记录 |
| 第 3 层 | Redis SETNX + Lua 原子释放 | 跨实例互斥 + 自旋重试 |

---

## 8. 面试轮次校验

```mermaid
flowchart TD
    A[HR 发送面试邀请] --> B{检查已有面试记录}
    
    B -->|无记录| C[ROUND_1]
    B -->|R1 已通过| D[ROUND_2]
    B -->|R2 已通过| E[ROUND_3]
    
    C --> F[创建 R1 面试记录<br/>interviewStatus=PENDING]
    D --> G{校验 R1 是否 PASSED}
    G -->|是| H[创建 R2 面试记录]
    G -->|否| I[抛异常<br/>"一面未通过，无法进入二面"]
    
    E --> J{校验 R2 是否 PASSED}
    J -->|是| K[创建 R3 面试记录]
    J -->|否| L[抛异常<br/>"二面未通过，无法进入三面"]
```

---

## 9. Agent 热插拔架构

```mermaid
flowchart LR
    subgraph Config[application.properties]
        P["hr.agent.resume-parse.enabled=true<br/>hr.agent.resume-parse.ai.mock=true<br/>hr.agent.resume-parse.ai.model=gpt-4o-mini"]
        Q["hr.agent.candidate-qualify.enabled=true<br/>hr.agent.candidate-qualify.ai.mock=true"]
    end

    subgraph Registry[AgentRegistry]
        A1[ResumeParseAgent<br/>@ConditionalOnProperty]
        A2[CandidateQualifyAgent<br/>@ConditionalOnProperty]
    end

    Config -->|配置注入| Registry
    Registry -->|execute("resume-parse", text)| A1
    Registry -->|execute("candidate-qualify", candidate)| A2
    A1 -->|mock=true| R1[正则提取]
    A1 -->|mock=false| R2[DeepSeek Chat API]
    A2 -->|mock=true| R3[加权评分]
    A2 -->|mock=false| R4[DeepSeek Chat API]
```
