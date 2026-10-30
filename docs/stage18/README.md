# Stage18：课程问答、评论与通用点赞

## 结构与接口

community-service 运行于 8090，Gateway 通过 `/community/** → lb://community-service` 转发并覆盖身份 Header。MySQL `campus_community` 中的 `question`、`answer`、`comment` 存内容，`like_relation` 存点赞关系，`event_outbox` 存待发积分事件。所有用户 ID 取自 Gateway 透传的身份上下文；服务端口应仅在可信网络内开放。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| POST | `/community/questions` | 创建问题，学生须有有效报名 |
| GET | `/community/questions?trainingId=&lessonId=&page=&size=` | 分页问题 |
| GET | `/community/questions/{id}` | 问题详情、回答、评论、实时点赞 |
| POST | `/community/questions/{questionId}/answers` | 创建回答并原子增加 `answer_count` |
| GET | `/community/questions/{questionId}/answers?page=&size=` | 分页回答 |
| POST/GET | `/community/comments` | 创建、按目标分页查询一层评论 |
| POST/DELETE/GET | `/community/likes` | 点赞、取消、查询当前用户点赞状态 |

请求中 `targetType` 仅接受 `QUESTION`、`ANSWER`。问题创建通过 Feign 检查培训；有课时/课程时核对归属。学生还通过 enrollment-service 检查有效报名。问题、回答、评论和点赞关系分别由 MySQL 本地事务或 Redis 原子脚本维护。

## 点赞实时层与刷盘

Redis Key 统一由 `LikeKeys` 管理：

- `campustrain:community:like:users:{type}:{id}`：Set，成员为当前点赞用户 ID；Set 自带去重，适合回答“谁点过赞”。
- `campustrain:community:like:count:{type}:{id}`：实时计数，便于直接读取。
- `campustrain:community:like:revision:{type}:{id}`：每次有效点赞/取消递增。
- `campustrain:community:like:init:{type}:{id}`：初始化标记。
- `campustrain:community:like:dirty`：待刷盘目标集合，成员 `{type}:{id}`。

首次访问在 Redisson 初始化锁内，从 MySQL 的有效 `like_relation` 重建 Set 和 count；锁仅用于初始化。点赞/取消用独立 Lua，一次完成 Set 去重、计数增减、revision 递增和 dirty 标记；重复点赞/取消返回 `changed=false`，计数不变且不会为负。

定时任务默认每 5 秒最多扫描 100 个 dirty 目标，在目标刷盘锁内读取 Set 快照和 revision。本地 MySQL 事务同步 `like_relation` 与 `question/answer.like_count`，事务成功后通过 Lua 比较 revision；只有版本未变化才清除 dirty。刷盘过程中有新操作时，dirty 保留给下一轮。一次目标的多次操作合并为一次关系差异写入，不在每个 HTTP 请求写 MySQL。

Redis 故障时点赞/取消返回 503；查询可回退 MySQL 已刷盘数据。完成刷盘后 Redis Key 丢失，可以从 MySQL 重建关系和计数。Redis 在刷盘前整体丢失时，未持久化的最近点赞变更可能丢失；MySQL、Redis 与 RabbitMQ 不构成分布式事务。

回答分页的实时点赞状态通过 Redis pipeline 批量读取；Redis 不可用时使用一次 MySQL 查询读取当前用户对本页回答的点赞关系，避免逐条网络往返。

## 问答积分

创建问题/回答的同一 MySQL 事务写入 `event_outbox NEW`，事件字段有 eventId、eventType、userId、targetId、targetType、actionType、occurredAt 和 traceId。定时 Publisher 向 `campustrain.business.exchange` 发送 `community.action`，只有 Confirm ACK 且没有 Return 才标记 `SENT`；失败保留 `NEW` 并重试。Growth 独立队列 `campustrain.growth.community.action.queue` 消费，按可配置规则给 QUESTION_CREATED +2、ANSWER_CREATED +5；`points_flow.event_id` 唯一键使重复消息不重复加分。Growth 保持原有学习完成队列、排行重建与 XXL-JOB 赛季结算。

## 初始化与验收

先执行 `community-service/sql/stage18-setup.sql`，向 Nacos `campustrain-dev` / `CAMPUS_TRAIN` 发布 `config/nacos/community-service-dev.yaml` 和更新的 `growth-service-dev.yaml`，启动 community-service、growth-service 及 Gateway、培训/报名/认证服务。SQL 不会因放在仓库中自动运行。可选的 `stage18-test-data.sql` 创建保留 ID 18001，`stage18-cleanup.sql` 只清理该保留样例；`stage18-consistency-check.sql` 每条查询正常时返回零行。运行 SQL 的 MySQL 客户端使用 `--default-character-set=utf8mb4`。

运行 `mvn -pl community-service,growth-service -am test`；真实 Redis/MySQL 集成测试设置 `STAGE18_LIVE_INTEGRATION=true`。测试会创建独立问题并定向清理。

验收重点：重复点赞只加一次，重复取消不减为负；多用户并发后实时 count、刷盘后的 `like_relation` 与 MySQL count 一致；删除该目标 Redis Key 后从 MySQL 恢复；旧 revision 不能清新 dirty；问答 Outbox 到 Growth 流水、账户和总榜/月榜均正确；RabbitMQ 暂停时 Outbox 保持 NEW，恢复后补发。

## 2026-10-30 开发环境实测

- community-service 8090 与 Growth 8088 启动并加载 Nacos；Gateway 登录后访问 `/community/questions/18002` 返回 200，未带 JWT 返回 401。请求伪造 `X-User-Id: 999` 后，数据库实际 `user_id=1001`。
- 创建问题后列表/详情可查；创建回答后 `question.answer_count=1`；评论列表查询成功。提问事件到账 +2、回答事件到账 +5；Outbox 均变为 `SENT`。重复投递同一 `eventId` 后 Growth 流水仍仅一条。
- 点赞、重复点赞、取消、重复取消依次得到 count 1/1/0/0，重复请求的 `changed=false`。真实 Redis/MySQL 集成测试的 40 个同用户并发点赞仅一条生效；4 个用户各重复 5 次后 count=4。刷盘后关系数与 count 均为 4，删 Redis Key 后可从 MySQL 重建。旧 revision 的清 dirty Lua 返回 0，dirty 保留。
- 暂停既有 RabbitMQ 容器时创建问题成功，Outbox 为 `NEW`；恢复后为 `SENT`，Growth 流水增加 2。RabbitMQ 启动约一分钟后可接入，其间 Outbox 重试，不会丢事件。
- 暂停既有 Redis 容器时点赞约 2.3 秒返回 503，问题详情约 4.1 秒从 MySQL 返回 200；恢复 Redis 后点赞查询正常。
- 一致性检查 SQL 的七条查询全部返回 0 行；community-service 8 项测试（含真实 Redis/MySQL 集成测试）和 growth-service 16 项测试通过；`mvn clean package -DskipTests` 通过。回答分页批量读取在运行服务上返回正确数据。
