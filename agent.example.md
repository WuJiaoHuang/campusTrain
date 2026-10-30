# CampusTrain Agent Context

## 项目与仓库
CampusTrain：高校内部培训与能力认证微服务平台。主线：Spring Cloud、分布式中间件、OSS 视频与学习进度、Docker、可观测性、AIOps。
当前目标：Stage18；已完成 Stage1~17，以代码及验收结果为准。分支：master。
开始前先检查远端与本地改动，不覆盖已完成阶段，按 Stage 顺序开发。

## 技术栈与服务
Java 17；Spring Boot 4.1.1；Spring Cloud 2025.1.3；Spring Cloud Alibaba 2025.1.0.0。
gateway 8080；user-service 8081；training-service 8082；enrollment-service 8083；auth-service 8084；learning-service 8087；growth-service 8088；community-service 8090（8089 为 XXL-JOB Admin）。
training 多实例：8082、8085、8086。

## 开发基础设施
虚拟机：192.168.10.102。不要重复部署已有容器，也不要删除容器。
MySQL：192.168.10.102:3306，用户名 root，MYSQL_PASSWORD=<YOUR_PASSWORD>；数据库 campus_user、campus_training、campus_enrollment、campus_learning、campus_growth、campus_community。
Redis：192.168.10.102:6379，REDIS_PASSWORD=<YOUR_PASSWORD>。
Nacos：192.168.10.102:8848；控制台 http://192.168.10.102:8848/nacos；Discovery DEFAULT_GROUP；Config CAMPUS_TRAIN，默认 namespace campustrain-dev，以实际配置为准。
Sentinel Dashboard：192.168.10.102:8858，版本 1.8.9。现有阈值分别为 training-detail 800、training-enrollment-check 750、enrollment-create 160、Gateway enrollment route 150 QPS/实例；不要随意修改。
RabbitMQ：192.168.10.102:5672，管理端口 15672，用户名 admin，RABBITMQ_PASSWORD=<YOUR_PASSWORD>，vhost /；已部署在 Docker 中，不要重复部署。
Stage15 使用 campustrain.business.exchange，routing key 为 enrollment.success 和 learning.completed；复用现有 Outbox、Confirm、Return、ACK、重试、DLQ 和消费者幂等拓扑。
Stage17 growth-service 监听独立队列 campustrain.growth.learning.completed.queue，并将 learning.completed 投影为 MySQL 积分事实和 Redis 总榜/月榜。
Stage18 community-service 通过 community.action 发布问答积分事件；growth-service 独立监听 campustrain.growth.community.action.queue。点赞 Redis 键前缀为 campustrain:community:like:，包含 users、count、revision、init、dirty。
XXL-JOB Admin：192.168.10.102:8089；executor appname campustrain-growth-executor，执行器端口 9998；账号或 Token 必须通过环境变量或安全渠道获取，禁止写入仓库。

## OSS
Bucket java-ai-wujiaohuang，region cn-beijing，endpoint https://oss-cn-beijing.aliyuncs.com，Bucket 为 private。
AccessKey 仅从 OSS_ACCESS_KEY_ID 和 OSS_ACCESS_KEY_SECRET 环境变量读取，不写入文件或 Git。learning-service 不持有 AccessKey，通过 training-service 获取临时播放 URL。

## 约束
使用 SLF4J、Logback、@Slf4j；禁止 System.out、System.err 和 printStackTrace；错误日志保留 Throwable，高频进度日志用 DEBUG。
SQL 由 Agent 负责，不能破坏无关数据库；测试数据必须一致。每阶段测试完成后按用户要求提交推送和创建 tag。
禁止提交 agent.md、.env、真实 AccessKey 或生产 Secret。
Stage18 只实现问答、回答、评论、通用点赞、写合并和问答积分事件；不实现 Stage19 或更后阶段。
