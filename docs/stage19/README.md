# Stage19：Docker Compose 全栈部署

## 部署范围

Compose 启动 MySQL、Redis、Nacos、RabbitMQ、XXL-JOB Admin、Nginx，以及 `campus-gateway`、`auth-service`、`user-service`、`training-service`、`enrollment-service`、`learning-service`、`growth-service`、`community-service`。所有容器加入 `campustrain-net`。浏览器业务入口为 `http://<VM_IP>/`（Nginx 80 → Gateway 8080）；业务容器不映射宿主机端口。管理入口：Nacos 8848、RabbitMQ Management 15672、XXL-JOB Admin 8089。MySQL/Redis 的宿主机绑定地址由 `.env` 控制，生产环境不要对公网开放。

## 前置条件与一键启动

Linux VM 安装 Docker Engine、Compose V2，预留足够的内存和磁盘空间；本项目 8 个 Java 进程与基础设施同机运行，低内存机器启动会很慢。当前使用绑定式命名卷，首次部署前执行 `sudo mkdir -p /root/mysql/data /root/redis/data`，或在 `.env` 中改为已有数据目录；先核对路径，避免意外启动一个空数据库。再执行 `mvn clean package -DskipTests` 产生八个可执行 JAR；Dockerfile 使用 Java 17 JRE，只复制 JAR，不包含源码或 Maven 缓存。复制 `.env.example` 为 `.env`，填写 MySQL、Redis、RabbitMQ、JWT、OSS 和 XXL-JOB 的真实环境值；`.env` 已被 Git 忽略。`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET` 只通过容器环境变量注入，不进入镜像。`JAVA_OPTS` 可调 JVM 内存。

```bash
docker compose config --quiet
docker compose build
docker compose up -d
docker compose ps
docker compose logs -f --tail=100 campus-gateway
```

日常重启使用 `docker compose restart`；停止使用 `docker compose down`。不要加 `-v`，也不要运行 `docker volume prune` 或 `docker system prune -a`。旧手工容器占用相同端口时，先备份并安全停止旧容器；不要删除原容器或数据卷。

## 数据和初始化

MySQL 数据卷 `campustrain-mysql-data` 在当前 VM 绑定到现有 `/root/mysql/data`，Redis 数据卷 `campustrain-redis-data` 绑定到 `/root/redis/data`，RabbitMQ 的 Compose 卷别名 `campustrain-rabbitmq-data` 指向现有 `rabbitmq-data`。实际路径可通过 `.env` 中 `MYSQL_DATA_PATH`、`REDIS_DATA_PATH` 调整；迁移旧环境前必须核对 Docker volume 与原数据目录。Nacos 配置和 XXL-JOB 任务保存在 MySQL 的 `nacos`、`xxl_job` 库；它们的日志另用命名卷保存。Redis 开启 AOF，RabbitMQ 使用持久卷。

`deploy/mysql/init/` 只在**空的 MySQL 数据目录首次初始化**时运行：创建业务库及表、Nacos 2.1.0 和 XXL-JOB 3.4.1 所需表。不会自动运行 Stage13 的破坏性重置数据脚本；业务测试数据需单独按对应阶段文档导入。已有数据库目录不会重新执行这些初始化脚本。正式切换前应备份 MySQL/Redis/RabbitMQ，并确认 Nacos/XXL-JOB 使用原有数据库。

## 容器寻址与健康检查

Compose 将 `MYSQL_HOST=mysql`、`REDIS_HOST=redis`、`NACOS_HOST=nacos`、`RABBITMQ_HOST=rabbitmq`、`XXL_JOB_ADMIN_ADDRESSES=http://xxl-job-admin:8080` 注入业务容器。Docker 内建 DNS 将服务名解析为同一网络中的容器地址；Feign 继续使用 Nacos 服务名发现。容器内不要使用 VM IP 来访问这些组件。Sentinel Dashboard 不属于本 Stage 的 Compose 范围，配置可通过 `SENTINEL_DASHBOARD` 指向宿主机现有 Dashboard。

MySQL、Redis、Nacos、RabbitMQ、XXL-JOB Admin 和业务服务均有健康检查，业务服务使用 `/actuator/health`。低内存双核 VM 的冷启动可能需数分钟，因此应用健康检查设有 600 秒启动宽限期。`depends_on` 只控制启动顺序；配置 `condition: service_healthy` 才能等待初始健康，运行时依赖仍可能故障，需查看应用日志和重试结果。RabbitMQ 健康检查探测 AMQP 5672 端口，避免资源紧张时 Erlang CLI 探针超时；业务消息可用性另通过实际发布/消费验证。

## 多实例与恢复

```bash
docker compose up -d --scale training-service=3
docker compose ps training-service
```

三个 training 容器各监听内部 8082，Nacos 注册三个实例，不映射宿主机 8082。可暂停其中一个容器并经 Nginx 重试详情和报名，确认 LoadBalancer/Feign 可切换；之后恢复容器。扩容与恢复后检查 Nacos 实例列表及健康状态。

持久化验证：记录业务表、Redis 键和 RabbitMQ durable 队列状态，执行 `docker compose restart mysql redis rabbitmq`，服务恢复健康后再次核对。RabbitMQ 管理台可检查 exchange、queue、DLQ；XXL-JOB Admin 在 8089 查看 `campustrain-growth-executor` 注册及任务执行。Nacos 在 8848 核对 Config 和 Discovery。

## 业务验收与常见故障

通过 `http://<VM_IP>/auth/login` 登录，再通过同一 Nginx 入口依次访问培训详情、报名、学习开始/进度、积分排行和 Stage18 社区接口。OSS 保持私有：training-service 在容器内生成临时上传/播放 URL，播放 URL 的实际 HTTP GET 应成功；不要在日志或报告中输出带签名 URL。

若容器启动慢，先看 `docker compose ps`，再用 `docker compose logs --tail=100 <service>` 定位。`unhealthy` 时检查容器内监听端口、依赖健康和内存/交换分区；Nacos Config 获取失败时核对 namespace ID、group 和 MySQL 中配置；RabbitMQ 连接失败时核对旧 volume 的节点 hostname、vhost 与权限；MySQL 初始化未执行时核对数据目录是否已有数据；OSS 403 时核对 Bucket ACL、AccessKey 权限和有效期。当前 VM 使用 CentOS 7 的 Linux 3.10 内核，较新的 `nginx:alpine` 镜像在默认 seccomp 下写 PID 文件会报 `Operation not permitted`，因此 Compose 固定使用实测可启动的 `nginx:1.24-alpine`。不要通过删除卷来修复启动问题。

Stage19 只做 Compose 部署与健康检查，不增加 Stage20 指标监控。
