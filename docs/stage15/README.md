# Stage15：RabbitMQ 异步、削峰与最终一致性

## 连接与拓扑

`enrollment-service` 使用 Spring Boot 4.1.1 的 `spring-boot-starter-amqp` 连接既有 RabbitMQ。连接地址可由 `RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`、`RABBITMQ_PASSWORD`、`RABBITMQ_VHOST` 覆盖；用户名是必填项，不能把未核实的默认账号用于真实环境。

| Exchange / Queue | Routing key | 用途 |
| --- | --- | --- |
| `campustrain.business.exchange` | `enrollment.success` | 报名成功通知 |
| `campustrain.enrollment.success.queue` | `enrollment.success` | 报名通知消费者 |
| `campustrain.business.exchange` | `learning.completed` | Stage16 学习完成事件，路由到 Stage17 积分队列 |
| `campustrain.growth.learning.completed.queue` | `learning.completed` | Stage17 积分消费者 |
| `campustrain.dlx` | `enrollment.success` | 报名通知队列的死信交换机 |
| `campustrain.enrollment.success.dlq` | `enrollment.success` | 报名通知失败三次后的死信队列 |

Exchange、Queue 均为 durable；消息使用 persistent delivery mode。

## 报名事件与 Outbox

Stage14 的 Redis Lua、Redisson 初始化锁、训练人数同步更新及数据库唯一约束保持不变。报名本地事务内同时写入 `enrollment` 与 `event_outbox`；发送由定时任务处理，不在报名请求线程同步连接 RabbitMQ。发送失败不会回滚已经成功的报名：Outbox 保持 `NEW`，定时任务每 5 秒按最多 50 条重发。只有 Publisher Confirm ACK 且没有 mandatory Return 才标记 `SENT`；NACK 和 mandatory Return 都保留 `NEW` 并记录 `retry_count` 与 `last_error`。

这保证的是本地业务与待发送事件的一致性，不是跨 MySQL、RabbitMQ、training-service 的全局事务。进程在 MQ ACK 与更新 Outbox 状态之间崩溃时可能重复投递，因此消费者必须幂等。

## 消费、ACK 与重试

报名通知消费者使用 manual ACK：解析消息、检查 `(event_id, consumer_name)`、写幂等记录和通知副作用都在同一 MySQL 本地事务中完成后才 ACK。相同 `eventId` 重投只会 ACK，不会重复产生副作用。失败时同步重试最多 3 次，仍失败执行 `basicReject(..., false)`，RabbitMQ 路由至报名通知 DLQ，不会无限 requeue。

Stage16 的真实学习进度 Producer 将 `learning.completed` 发布到业务交换机；Stage17 的独立积分消费者从 Growth 队列处理事件。enrollment-service 只消费报名成功通知。

## 初始化与检查

手动执行以下 SQL，项目不会自动执行它们：

```powershell
mysql -u root -p campus_enrollment < enrollment-service/sql/stage15-setup.sql
mysql -u root -p campus_enrollment < enrollment-service/sql/stage15-consistency-check.sql
```

`stage15-cleanup.sql` 只清理以 `stage15-` 为前缀的验证数据和测试通知，绝不清空正常报名数据。

## 真实验收步骤

1. 设置实际 RabbitMQ 用户名、密码和 vhost，启动 `enrollment-service`，在管理台确认报名通知队列及其 DLQ 被声明。
2. 用 Stage14 的真实学生完成报名。查询 `event_outbox` 应先出现记录，收到 Confirm ACK 后为 `SENT`；`notification_record` 只产生一条，`mq_consume_record` 有对应幂等记录。
3. 用错误 routing key 发布测试消息，验证 mandatory Return 被记录且 Outbox 不会标记 `SENT`。
4. 插入 `event_type=STAGE15_UNROUTABLE` 的测试 Outbox，可验证 mandatory Return 会使记录保持 `NEW`。报名通知消费失败时仍按原有重试和 DLQ 机制处理。
5. 对同一个 `eventId` 发布两次，副作用记录仍只有一条。
6. 停止消费者或应用，发布 100 条测试消息，观察 ready 堆积；恢复后 ready 回到 0，检查副作用记录无重复。
7. 暂停 RabbitMQ 后完成真实报名：报名和 Outbox `NEW` 必须存在。恢复 RabbitMQ 后等待定时任务，Outbox 变 `SENT` 并产生一次通知记录。

RabbitMQ 可用性、消费者处理成功和最终副作用是三个不同阶段；“发送调用没有抛异常”本身不代表业务已经完成。

2026-10-26 开发环境实测：直接调用 enrollment-service，暂停既有 RabbitMQ 容器后报名返回 HTTP 200，Outbox 为 `NEW`；恢复容器后该记录变为 `SENT`。这是直连服务的故障测试，不代表经过 Gateway 的端到端验收。此前同步发送时请求超过 45 秒未返回，已改为定时投递。测试留下的报名记录仍需按业务规则维护，不能直接删除而不处理人数和 Redis 状态。
