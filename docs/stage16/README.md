# Stage16：学习进度与断点续播

## 调用链

```text
播放：Client → Gateway → learning-service → training-service → OSS Presigned GET
进度：Client → learning-service → Redis progress hash → dirty set → 定时刷盘 → MySQL
完成：Redis 首次完成 → MySQL learning_record + 本地 event_outbox → RabbitMQ learning.completed → Stage17 Growth 积分消费者
```

Gateway 校验 JWT 并覆盖身份 Header；learning-service 使用请求上下文中的用户 ID，不能从请求体指定用户。服务间接口不经 Gateway 暴露，但本阶段内部接口和身份 Header 未做服务间签名认证，必须通过网络隔离确保客户端无法直连各服务端口，不能将内部接口直接暴露到公网。

## 初始化

手动执行 `learning-service/sql/stage16-setup.sql`；SQL 不会自动运行。向 Nacos `campustrain-dev` 命名空间、`CAMPUS_TRAIN` 分组发布 `config/nacos/learning-service-dev.yaml`，启动 learning-service（8087）以及 Gateway、training-service、enrollment-service 和已有基础设施。敏感信息使用环境变量或本地 `.env`，不要提交密钥。

可选的 `stage16-test-data.sql` 使用保留 ID 16001～16003 和现有 student01、teacher01；执行前确认这些 ID 未被其他数据使用。脚本不伪造 OSS 对象。`stage16-cleanup.sql` 仅清理这些 SQL 测试数据；清理前必须确认 ID 所属，并停止服务、另外定向删除对应 Redis 键和 dirty 成员。该 SQL 不删除 OSS 对象，如需清理视频，应在删除课时记录前通过 Stage11 的视频删除接口单独处理。`stage16-consistency-check.sql` 的每条查询正常时均应返回零行。

## API

均经 Gateway，要求学生身份和有效报名：

| 方法和路径 | 作用 |
| --- | --- |
| POST `/learning/lessons/{id}/start` | 视频信息、短期播放地址、最高有效断点 |
| POST `/learning/lessons/{id}/progress` | 请求体 `{"progressSeconds":30}`，报告视频进度 |
| GET `/learning/lessons/{id}/progress` | 查询进度；Redis 不可用时可读取已持久化值 |
| POST `/learning/lessons/{id}/complete` | 幂等完成图文课时 |
| GET `/learning/chapters/{id}/progress` | 章节完成度 |
| GET `/learning/trainings/{id}/progress` | 培训完成度 |

视频必须已经通过 Stage11 绑定真实对象且时长大于零；非视频不能获取播放地址。学习服务不持有 OSS AccessKey，不保存永久播放 URL。新建的学生学习记录与测试数据不改变其他阶段表结构。

## 进度与恢复

Redis Hash：`campustrain:learning:progress:{userId}:{lessonId}`；集合 `campustrain:learning:dirty` 的成员为 `{userId}:{lessonId}`。Lua 原子比较进度并维护单调递增的 revision；旧值不覆盖新值。用户可以在播放器拖回观看，但服务器断点代表最高有效进度，不随拖动回退。视频达到 `campustrain.learning.complete-ratio`（默认 0.90）后不可逆地完成；图文课由显式接口完成。

定时任务默认每 5 秒取最多 100 个 dirty 项，每项在本地事务中进行单调 MySQL upsert；同一次事务在完成时插入 Outbox，数据库唯一键 `(user_id, lesson_id, event_type)` 保证一个课时只有一个逻辑完成事件。只有提交成功且 Redis revision 没变化，Lua 才移除 dirty 成员；并发上报不会被旧批次错误清除。发送器使用 mandatory Return 和 Publisher Confirm，失败保留 `NEW` 重试；MQ 可能重复投递，Stage17 Growth 积分消费者按 eventId 幂等。

Redis progress 键缺失时从 MySQL 恢复并原子初始化，不覆盖并发新值。Redis 不可用时写入返回 503，不退化成每次写 MySQL；读取可以使用持久化进度。Redis 完全丢失且其中有尚未刷盘的数据时，最近一个刷盘周期的进度可能丢失；本方案并不提供 Redis 与 MySQL 的分布式事务。读到的完成度会结合 Redis 和 MySQL，Redis 故障时只能反映已持久化值。

## 测试与当前验收

运行 `mvn -pl learning-service test`；真实 Redis/MySQL 集成测试需要设置 `STAGE16_REDIS_INTEGRATION=true` 和 `STAGE16_MYSQL_INTEGRATION=true`，使用开发环境变量配置连接。测试只清除自己生成的隔离 Redis 键及指定的 Stage16 测试记录。

2026-10-26 已实际验证：Gateway JWT 覆盖伪造身份；未报名返回 403；图文完成、章节/培训完成度、重复完成唯一 Outbox；Redis Lua 乱序/并发/版本竞争；清除已刷盘进度键后从 MySQL 恢复；RabbitMQ 暂停时完成记录和 `NEW` Outbox 落库，恢复后 `SENT`。Redis 故障通过指向不可达端口的隔离实例模拟，读取 MySQL 成功、写入返回 503，并未停止现有 Redis。另有 MockMvc 测试使用真实 Redis、MySQL 和模拟 Feign：10 次进度 HTTP 上报在一次刷盘中产生 1 次 MySQL 写入，最终进度为 10；这不是实际视频播放的端到端测试。

补充端到端验收：已确认 Bucket 为 private，通过 Stage11 上传并绑定真实的 100 秒测试视频；Gateway 学习开始接口返回临时播放地址，实际 HTTP GET 成功且下载文件哈希与上传文件一致。视频进度 10、20、30、40、60、50 最终为 60；20 个并发上报最终为最大值 80。达到 90% 后完成，继续上报至 100 秒仅产生一个完成事件，Outbox 为 SENT；章节和培训完成度均为 3/3。删除已刷盘的 Redis 视频进度键后，从 MySQL 恢复为 100 秒。真实视频链路的 MySQL 写入次数尚未单独测量，上述 10 次请求合并为 1 次写入的结果来自 MockMvc 集成测试。
