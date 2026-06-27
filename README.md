# HR-manager-project
人力HR管理系统
1. ✅ PDF双校验 — Content-Type + Magic Bytes + PDFBox解码
2. ✅ Redis Streams — XADD/XREADGROUP/XACK 带消费确认
3. ✅ 批量落盘 — 积累满N条或定时批量写入MongoDB
4. ✅ 状态机 — 8种状态 + 6条流转规则 + Redis分布式锁 + Event Sourcing审计
5. ✅ Agent热插拔 — @ConditionalOnProperty 配置注入启停
6. ✅ 岗位隔离 — 候选人列表按岗位，面试/Offer/入职跨岗位
7. ✅ 备选列表 — PENDING_ARCHIVE按岗位分组 + 恢复功能
8. ✅ 面试流转 — Round 1→2→3校验 + 三面通过进入待发Offer队列
