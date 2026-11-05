# AIOps Stage 1：Prometheus 与 Grafana 可观测性

本阶段只建立指标采集基础设施，不包含 Loki、Promtail、OpenTelemetry、告警、AI Agent 或自动修改 Sentinel。

```text
Business Request
       ↓
CampusTrain Service
       ├── Business Logic
       └── Micrometer
              ↓
      /actuator/prometheus
              ↓
         Prometheus
              ↓
           Grafana
```

## 接入服务与访问地址

8 个 Java 服务均保留 `spring-boot-starter-actuator`，并增加由 Spring Boot dependency management 管理版本的 `io.micrometer:micrometer-registry-prometheus`：`campus-gateway:8080`、`auth-service:8084`、`user-service:8081`、`training-service:8082`、`enrollment-service:8083`、`learning-service:8087`、`growth-service:8088`、`community-service:8090`。

- Prometheus：`http://192.168.10.102:9090`
- Grafana：`http://192.168.10.102:3000`
- Java 服务内部抓取：`http://<service-name>:<port>/actuator/prometheus`

Prometheus 不经过 Nginx 或 Gateway，直接通过 `campustrain-net` 抓取 Docker DNS 服务名，同时抓取自身 `localhost:9090`。

## Actuator 与指标

公共 Nacos 配置 `campustrain-common-dev.yaml` 开放 `health`、`info`、`prometheus`、`metrics`。`/actuator/health` 保持现有 Docker healthcheck 使用方式；没有开放 `env`、`beans`、`configprops`、`heapdump` 或 `threaddump`。

HTTP 指标由 Spring Boot/Micrometer 自动 instrumentation 产生。公共 Nacos 配置已为 `http.server.requests` 开启 histogram，预期并在运行态验收中检查 `http_server_requests_seconds_count`、`http_server_requests_seconds_sum` 和 `http_server_requests_seconds_bucket`，通常带有 `method`、`uri`、`status`、`outcome` 标签。最终以对应服务 `/actuator/prometheus` 的实际输出为准，不在业务 Controller 中手写计数器。

## Prometheus Targets

`deploy/prometheus/prometheus.yml` 包含 9 个 job：8 个 CampusTrain Java 服务和 Prometheus 自监控。每个 Java target 使用 `/actuator/prometheus`；运行态以 `/api/v1/targets` 查询结果确认 `up`。

## 核心 PromQL

```promql
# 服务是否存活
sum by (job) (up{job=~"$service"})

# HTTP QPS
sum by (job) (rate(http_server_requests_seconds_count{job=~"$service"}[1m]))

# HTTP 5xx rate
sum by (job) (rate(http_server_requests_seconds_count{job=~"$service",status=~"5.."}[1m])) /
sum by (job) (rate(http_server_requests_seconds_count{job=~"$service"}[1m]))

# HTTP P95
histogram_quantile(0.95, sum by (job, le) (rate(http_server_requests_seconds_bucket{job=~"$service"}[5m])))

# JVM heap / non-heap
sum by (job, area) (jvm_memory_used_bytes{job=~"$service"})

# Process CPU / live threads / GC pause
process_cpu_usage{job=~"$service"}
jvm_threads_live_threads{job=~"$service"}
rate(jvm_gc_pause_seconds_sum{job=~"$service"}[5m])
```

若当前 Spring Boot/Micrometer 版本对某个 meter 使用了不同名称，先从 `/actuator/prometheus` 查实际名称，再调整查询，不把不存在的名称当作验收证据。

## Grafana Dashboard

`deploy/grafana/dashboards/campustrain-overview.json` 提供 `CampusTrain Overview`，由 provisioning 自动加载，包含 Service Up、HTTP QPS、HTTP P95、HTTP 5xx Rate、JVM Heap Used、Process CPU、JVM Threads、GC Pause。所有面板（包括 Service Up）都使用 `service` 变量按 job 筛选；选择 `learning-service` 时只显示该服务。

Grafana 管理员密码只从 `.env` 的 `GRAFANA_ADMIN_PASSWORD` 读取，`.env.example` 只含占位符。数据源由 provisioning 自动配置为 `http://prometheus:9090`。

## Docker 部署

Prometheus 使用固定版本 `prom/prometheus:v2.55.1`，保留 7 天数据；Grafana 使用固定版本 `grafana/grafana:11.3.0`。两者加入 `campustrain-net`，使用命名卷 `campustrain-prometheus-data`、`campustrain-grafana-data`，并设置 `restart: unless-stopped` 与健康检查。

```bash
mvn clean package -DskipTests
docker compose build prometheus grafana
docker compose up -d prometheus grafana
docker compose ps
```

不执行 `docker compose down` 或 `docker compose down -v`，不删除现有业务数据卷。

## 验收与限制

验收至少确认：8 个 Java 服务的 `/actuator/prometheus` 可访问；Prometheus `/api/v1/targets` 显示 8 个业务 target 和自身均 `up=1`；Prometheus API 能查询 `up`、实际 HTTP meter、JVM memory、CPU、threads、GC；Grafana `/api/health` 返回 200，数据源和 Dashboard 能查询真实数据；触发登录、培训详情、学习进度查询后，QPS、状态和延迟出现样本。

### 本次收尾验收记录（2026-11-05）

- histogram 配置：已写入并成功同步 Nacos `CAMPUS_TRAIN/campustrain-dev/campustrain-common-dev.yaml`，配置键为 `management.metrics.distribution.percentiles-histogram.http.server.requests: true`。
- 本地配置、Dashboard JSON、Compose 配置和文档校验：通过；Nacos GET 回读内容与仓库配置一致。
- 运行态：未完成本次版本部署。当前 VM 的 Prometheus 返回的是旧的 `monitor-demo` target（`up=0`，目标为 `192.168.10.1:8080`），不是本项目 Compose 中的 9 个 target；VM Grafana `:3000` 和业务 Actuator 端口当前拒绝连接。
- 因 SSH 公钥/密码认证失败且远程 Docker `2375` 未开放，本次没有上传 JAR、重启或修改任何现有容器，也没有执行 `docker compose down`/`down -v`。
- 因运行态未切换到本次配置，9/9 targets、Prometheus histogram 实际样本、Grafana 数据源和 8 个 Panel 不能标记为通过；待恢复 VM 部署权限后，需按上文命令执行无破坏滚动部署并重新验收。

本阶段不建立告警规则，也不根据指标自动修改 Sentinel；Prometheus/Grafana 仅作为后续 AIOps 阶段的只读指标来源。
