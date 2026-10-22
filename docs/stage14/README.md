# Stage14: Redis Lua + Redisson 高并发报名

## 并发边界

报名预占由 Redis 同一个 Lua 脚本完成：检查重复用户、检查剩余名额、扣减库存、加入有效用户集合。脚本在 Redis 内原子执行，避免 Stage12 中“检查再扣减”的进程间竞态。数据库的 `uk_user_training(user_id, training_id)` 仍然保留为最终兜底，Lua 不替代唯一约束。

Key 统一在 `EnrollmentRedisKeys`：

| Key | 含义 |
|---|---|
| `campustrain:enrollment:stock:{trainingId}` | 剩余名额 |
| `campustrain:enrollment:users:{trainingId}` | 有效报名用户集合 |
| `campustrain:enrollment:init:{trainingId}` | Redis 状态初始化标记 |
| `campustrain:enrollment:init-lock:{trainingId}` | 初始化分布式锁 |

第一次报名会从 training-service 查询容量和状态，并从 enrollment 数据库加载 `status=1` 的用户，计算 `stock = capacity - activeEnrollmentCount`。该初始化使用 Redisson `RLock.tryLock`，只有获得锁的线程才会解锁。未指定 lease time，因此 Redisson WatchDog 会在持锁线程存活时自动续期；`RLock` 也具备可重入语义。Redisson 仅用于低频初始化，正常报名绝不按请求加锁。

## 主流程与补偿

1. 校验 JWT/Gateway 用户上下文、学生身份、用户状态、培训状态及报名窗口。
2. 初始化 Redis 报名状态（若需要），执行 reserve Lua。
3. 预占成功后同步调用 training-service 原子加计数，再本地插入或重新激活报名记录。
4. 加计数或本地写入失败时，调用 training-service decrement（如已加）并运行 release Lua；release 只在用户确实存在于集合时归还库存。
5. 取消报名同步减 training 计数，并执行 release Lua。提交失败时会尝试重新 reserve Redis 状态和 restore 远程计数。

Redis 不可用时，报名直接返回 HTTP 503 和“报名高并发控制暂时不可用，请稍后重试”，不会退化为旧的 DB 抢名额流程。Redis、MySQL 和远程 training-service 不是分布式事务；网络结果未知或进程在跨系统步骤间崩溃仍可能留下不一致风险，Stage15 才使用消息机制处理最终一致性。

## 压测数据与验收

1. 执行 `enrollment-service/sql/stage14-setup.sql`（100 名额培训 `14001` 与 1000 个学生，密码均为 `123456`）。
2. 每次测试前执行 `stage14-reset.sql`，再运行 `scripts/stage14/reset-redis.ps1`。
3. 启动临时 user/training/enrollment 服务后，按 `scripts/stage14/jmeter/README.md` 执行两个非 GUI JMeter 计划。
4. 执行 `stage14-consistency-check.sql`，并在 Redis 验证：`GET stock`、`SCARD users`。

必须满足：`active enrollment count = training.enrolled_count = SCARD(users) = 100 - stock`，库存不为负且没有重复 `(user_id, training_id)`。

## 真实验收结果（2026-10-22）

测试拓扑：`user-service=1 (18081)`、`training-service=1 (18082)`、`enrollment-service=1 (18083)`、MySQL=1、Redis=1，服务通过 Nacos `STAGE14_TEST` 组发现；JMeter 直接请求 isolated enrollment-service，并以 Stage7 可信身份 Header 构造用户上下文。

| 场景 | 压力 | 结果 | 一致性 |
|---|---:|---|---|
| 100 名额 / 1000 不同学生 | 1000 请求，25 秒 ramp，约 40 req/s | 200=100，400=900，500=0 | active=100，training count=100，Redis stock=0，SCARD=100 |
| 同一学生重复报名 | 100 请求，5 秒 ramp，约 20 req/s | 200=1，400=99 | active=1，training count=1，Redis stock=99，SCARD=1，数据库仅一行 |

JTL 和 HTML 报表实际保存在 `scripts/stage14/results/`，但被 Git 忽略以避免提交大量运行产物；可按 JMeter README 的命令重现。首轮 10 秒陡增运行曾暴露数据库 `FOR UPDATE` 间隙锁死锁，已移除首次报名查询中的不必要锁，最终结果使用修复后的压测运行。
