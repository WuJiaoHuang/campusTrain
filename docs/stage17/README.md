# Stage17：积分、排行榜与 XXL-JOB 赛季结算

## 调用链

```text
learning-service Outbox
  -> campustrain.business.exchange (learning.completed)
  -> campustrain.growth.learning.completed.queue
  -> growth-service
  -> campus_growth.points_flow + points_account
  -> Redis ZSET 总榜/月榜
```

`points_flow.event_id` 有唯一键，是积分幂等边界。重复投递只会被确认，不会重复增加账户或排行榜积分。

## 排行榜与恢复

- 总榜：`campustrain:growth:ranking:total`
- 月榜：`campustrain:growth:ranking:month:yyyy-MM`
- `GET /growth/rankings/total?limit=10`
- `GET /growth/rankings/monthly?month=yyyy-MM&limit=10`
- `GET /growth/rankings/me`、`GET /growth/rankings/me/monthly?month=yyyy-MM` 从 Gateway 透传的 `X-User-Id` 获取本人。

MySQL 是事实来源，Redis 是可删除、可重建的投影。XXL-JOB handler `growthRankingRebuildJob` 的参数为可选月份；例如 `2026-10` 会同时重建总榜和该月榜。

## 赛季结算

XXL-JOB handler `growthSeasonSettlementJob` 参数为 `yyyy-MM`；未给参数时结算上海时区上月。结算将该月 `points_flow` 汇总写入 `ranking_history`，并创建下一个月的 ACTIVE 赛季。`ranking_season`、`ranking_history(season_id,user_id)`、`job_execution_record(job_name,biz_key)`共同保证重复执行不会重复排名或结果。

Admin 地址默认 `http://192.168.10.102:8089`，executor appname 为 `campustrain-growth-executor`、端口为 `9998`。密码和 token 只使用环境变量/本地配置，禁止提交。

部署使用 VM 的既有 MySQL 容器和 `hm-net` 网络：先在 `xxl_job` 库执行 XXL-JOB 3.4.1 官方 `tables_xxl_job.sql`，再运行 `xuxueli/xxl-job-admin:3.4.1` 容器 `xxl-job-admin`，端口映射 `8089:8080`，重启策略 `unless-stopped`。Admin 使用既有 MySQL 地址 `mysql:3306`；Growth 通过 `XXL_JOB_ADMIN_ADDRESSES`、`XXL_JOB_ACCESS_TOKEN`、`XXL_JOB_EXECUTOR_IP` 连接它。本机与 Docker 容器联调时执行器 IP 需填写容器可达的宿主机地址；本次为 `192.168.10.1`，存于被 Git 忽略的 `.env`。

在 Admin 中注册执行器 `campustrain-growth-executor`，创建两个 BEAN 任务：`growthSeasonSettlementJob` 和 `growthRankingRebuildJob`。手工执行时传月份参数 `yyyy-MM`；调度可按业务需要配置到月初。可在管理台任务日志核对 trigger/handle 均为 200，并在 `xxl_job_registry` 核对在线注册。

## SQL 与验收

创建 `campus_growth` 时执行 `growth-service/sql/stage17-setup.sql`。本次开发环境已实际执行。测试期间可使用：

- `stage17-test-data.sql`：重建用的幂等样例数据
- `stage17-consistency-check.sql`：四条检查均应返回 0 行，包括历史排名的 MySQL 事实核对
- `stage17-cleanup.sql`：仅在十月尚未结算时清除样例；已结算快照保持不变，防止误删真实学习者的历史名次

消费者仅在积分事实提交后 ACK；失败通过独立 retry 队列 5 秒后重新消费，三次重试后进入独立 dead 队列。积分队列独立于 Stage15 其它消费者，重复投递由 `event_id` 唯一键安全处理。

## 本次真实验收（2026-10-28）

- `growth-service` 8088 启动，注册 Nacos；Gateway 8080 的 `/growth/**` 带 JWT 正常转发，不带 JWT 返回 401。伪造的 `X-User-Id: 17001` 被 Gateway 覆盖，学生 1001 的个人排名仍返回其真实身份。
- Stage16 的 `POST /learning/lessons/5001/complete` 产生 Outbox 消息，`eventId=0fceb3e6-a38d-4464-9d11-7e4a7c12897c`，Growth 真正消费后账户与流水各增加 10；重复投递相同 eventId 后账户、流水、Redis 分数仍为 10。
- 同一事件还被 Stage15 的独立审计消费者记录在 `campus_enrollment.learning_event_audit`，两套队列没有抢消息。
- 示例三个用户总榜/月榜分别为 17001=30、17002=20、17003=10；通过 XXL-JOB 删除后重建总榜和月榜成功。
- 用错误的 `REDIS_PORT=6380` 启动 Growth 并投递测试事件，MySQL 的 `points_flow`、`points_account` 成功记 10 分，Redis 不更新；恢复 6379 并执行 `growthRankingRebuildJob` 后两榜均恢复 10 分。
- XXL-JOB Admin 3.4.1 容器在 VM 的 `hm-net` 上运行，管理端口 8089；`campustrain-growth-executor` 在 9998 在线注册。手工触发 `growthSeasonSettlementJob`，2026-10 结算为 SETTLED，2026-11 为 ACTIVE，历史排名 5 条；同月再执行仍为 5 条、任务执行记录 1 条，管理端 trigger/handle 均为 200。
- `mvn -pl growth-service test` 与全项目 `mvn clean package -DskipTests` 均已通过。MySQL 一致性检查 4 条均返回 0 行。

本地开发环境的当前月结算用于验收；实际运行应只结算已结束的月份。历史榜是结算时的快照。Redis 榜在同分时仅保证积分降序，历史榜同分再按 `user_id` 升序稳定排序。
