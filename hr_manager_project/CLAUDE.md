# CLAUDE.md

本文档为 Claude Code 在此仓库工作时提供指引。

## 构建与运行命令

```bash
# 后端 - 编译（跳过测试加速）
cd hr_manager_project && mvn clean compile -DskipTests

# 后端 - 启动（需本地 MongoDB + Redis）
mvn spring-boot:run

# 后端 - 使用本地配置启动（API密钥等敏感信息在 application-local.properties）
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 后端 - 运行测试
mvn test

# 后端 - 快速编译检查
mvn compile -q

# 前端 - 开发服务器（端口 5173，/api 代理到后端 :8080）
cd hr-frontend && npm run dev

# 前端 - 生产构建
npm run build
```

## 环境要求

- Java 17+
- MongoDB（默认 localhost:27017）
- Redis（默认 localhost:6379，有密码则在 application-local.properties 中配置）
- Node.js 20+
- Maven 命令行

## 架构概览

**后端**：Spring Boot 3.5.16 + Java 17，提供 REST JSON API（端口 8080）。  
**前端**：Vue 3 + Vite 6 + Element Plus + Pinia，SPA 单页应用（端口 5173）。  
**数据库**：MongoDB（主存储）+ Redis（流队列、缓存、发布订阅、分布式锁）。

两个独立前端模块共享同一后端 API：
- **求职端** — `/seeker/*` 路由
- **HR管理端** — `/hr/*` 路由

## 双项目结构

```
hr_manager_project/     ← Spring Boot 后端（Maven）
hr-frontend/            ← Vue 3 前端（npm/Vite）
```

Vite 开发服务器将 `/api` 和 `/uploads` 代理到 `localhost:8080`。

## 简历上传与解析管道

支持两种路径：**同步路径**（Vue 前端主用）和**异步路径**（Redis Streams，旧版）。

### 同步路径（Vue 前端使用）
```
求职者选岗位 → 上传 PDF → POST /api/resume/parse-sync
  → PdfValidator（大小 + Content-Type + PDFBox 解码）
  → FileStorageUtil（保存到 ./uploads/）
  → PDFBox 文本提取
  → ResumeParseAgent（AI 解析，正则 mock 或 OpenAI API）
  → 返回解析后的 Candidate JSON 到前端
  → 求职者在表单中编辑补充字段（姓名、电话、学历、技术栈等）
  → POST /api/resume/submit → MongoDB 保存 + 关联求职者
```

### 异步路径（旧版 Redis Stream 方式）
```
POST /api/resume/upload → Redis Stream 队列 "parsing_queue"（XADD）
  → ResumeParserConsumer（@Scheduled 每2秒，XREADGROUP/XACK）
  → PDFBox 文本提取 + ResumeParseAgent AI 解析
  → Redis 临时存储（resume_temp:{id}，30分钟 TTL）
  → ResumeBatchPersistenceService（@Scheduled 批量落盘）
  → MongoDB "candidates" 集合
  批量触发条件：积累 10 条 或 每 5 秒
  （配置参数：hr.resume.batch.size、hr.resume.batch.interval-ms）
```

## Agent 热插拔架构

```java
HrAgent<T, R> 接口 → AgentRegistry（DI 收集已启用的 Agent）
  ├── ResumeParseAgent       — hr.agent.resume-parse.enabled=true
  └── CandidateQualifyAgent  — hr.agent.candidate-qualify.enabled=true
```

每个 Agent 拥有独立的 AI 配置命名空间：
```properties
hr.agent.resume-parse.ai.mock=true
hr.agent.resume-parse.ai.api-key=
hr.agent.resume-parse.ai.model=gpt-4o-mini

hr.agent.candidate-qualify.ai.mock=true
hr.agent.candidate-qualify.ai.api-key=
hr.agent.candidate-qualify.ai.model=gpt-4o-mini
```

修改 `enabled` 配置可启停 Agent。默认模拟模式（正则提取），需要真实 AI 时将 `mock=false` 并在 `application-local.properties` 中填入 API Key。

## 状态机（8 状态，3 层一致性保障）

```
NEW ──────────────────────────────────────────────┐
  ├──→ PENDING_ARCHIVE（存档待定 → 备选库）        │
  ├──→ INTERVIEW_INVITED（HR 邀约面试）              │
  │       └→ 求职者接受 → IN_INTERVIEW              │
  │       └→ 求职者拒绝 → REJECTED                  │
  └──→ REJECTED（淘汰）←───────────────────────────┤
                                                      │
IN_INTERVIEW（一面/二面/三面进度）                     │
  ├──→ 三面通过 → WAITING_OFFER（待发 Offer 队列）   │
  ├──→ 任一环节失败 → REJECTED                        │
  └──→ OFFERED ← HR 在待处理页发 Offer               │
           ├→ 求职者接受 → ONBOARDED                  │
           └→ 求职者拒绝 → REJECTED                   │
                                                      │
PENDING_ARCHIVE ↔ NEW（从备选库恢复）
```

三层一致性：(1) MongoDB `@Version` 乐观锁，(2) Event Sourcing 通过 `statusHistory[]` 记录每次变更，(3) Redis `SETNX` 分布式锁防止并发。

状态转换在 `CandidateService.validateStatusTransition()` 中校验。面试轮次在 `InterviewService.saveInterview()` 中强制执行（ROUND_1 → ROUND_2 → ROUND_3 顺序；第 3 轮通过后自动推进到 WAITING_OFFER）。

## 岗位实时同步

```
HR 增删改岗位
  → ApplicationEventPublisher 发出 PositionChangeEvent
  → @Async @EventListener 异步处理：
      1. 清除 Redis 缓存键 "cache:positions"
      2. 发布到 Redis Pub/Sub 频道 "position:channel"
  → RedisMessageListener（所有实例收到通知）
  → SseEmitterManager.broadcast() 推送给所有 SSE 客户端
  → Vue 前端 EventSource 收到 "position-change" 事件
  → UploadView / PositionManageView 自动刷新列表
```

SSE 端点：`GET /api/hr/positions/events`（断线自动重连）。

## Redis 岗位缓存（旁路缓存模式）

```
GET /api/hr/positions/list
  → 查 Redis "cache:positions"
    → 命中 → 直接返回缓存 List<Position>
    → 未命中 → MongoDB findAll() → Redis SETEX 300秒 → 返回
```

每次写操作（创建/更新/删除）后清除缓存。5 分钟 TTL 作为安全兜底。

## 每个职位只能投递一次，但可投多个职位

求职者可同时投递多个不同岗位，但**每个岗位只能有一个进行中的申请**，直到 HR 处理完毕（进入终态 PENDING_ARCHIVE 或 REJECTED）才能再次投递同一岗位。

实现：`Seeker.positionCandidates`（Map<String, String> 岗位名 → 候选人ID）。  
`canSubmit(seekerId, position)` 通过 `CandidateRepository.findBySeekerIdAndPosition()` 查询该岗位是否有非终态的候选人。

## 后端关键配置（application.properties）

```properties
# 批量落盘
hr.resume.batch.size=10
hr.resume.batch.interval-ms=5000

# Agent 开关（每个有独立 mock/key/model）
hr.agent.resume-parse.enabled=true
hr.agent.candidate-qualify.enabled=true
hr.agent.resume-parse.ai.mock=true
hr.agent.candidate-qualify.ai.mock=true

# 文件上传
spring.servlet.multipart.max-file-size=10MB
app.resume.upload-dir=./uploads
```

本地敏感配置（API Key、Redis 密码）放在 `application-local.properties`（已被 .gitignore 排除）。

## 前端 API 层

所有 API 调用通过 Axios 实例发起，定义在 `hr-frontend/src/api/index.js`。两个命名空间：

- `seekerApi.*` — 登录、状态查询、投递检查、上传简历、响应面试/Offer、更新候选人、同步解析、提交
- `hrApi.*` — 岗位 CRUD、候选人列表/详情/状态/AI评定、面试、Offer、入职、备选

响应格式：`{ code: 0, message: "success", data: ... }`。Axios 拦截器自动剥离外层，直接返回 `res.data`。当 `code !== 0` 时自动弹出错误提示。

SSE 辅助函数 `subscribePositionEvents(onEvent)` 返回清理函数，用于在组件销毁时取消订阅。

## 重要编码模式

- **构造器注入** — 禁止使用 `@Autowired` 字段注入
- **Lombok `@Data`** — 所有实体类使用，Maven 已配置 annotationProcessorPaths
- **候选人筛选** — 内存中通过 `CandidateService.applyFilters()` 流式处理（非 MongoDB 原生查询）
- **求职者自动登录** — `useSeekerStore.ensureSeeker()` 首次访问自动创建演示账号，无需密码鉴权
- **Vue Router 懒加载** — 所有路由使用动态 `import()` 按需加载视图组件
- **文件命名** — Vue 组件使用 PascalCase；视图放在 `views/hr/` 和 `views/seeker/` 目录下
