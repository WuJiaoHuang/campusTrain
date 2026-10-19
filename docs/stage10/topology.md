# Stage 10 测试拓扑

状态：A/B 已实测（2026-10-19），C 未测试。本文件记录实际运行拓扑；计划定义见 [benchmark-plan.md](benchmark-plan.md)。

## 1. 实际环境

| 项 | 实际值 |
| --- | --- |
| 测试时间 | 2026-10-19 00:17–01:58（Asia/Shanghai） |
| Java 服务主机 | 单台 Windows 11（本机），所有 Java 进程同机 |
| 压测客户端 | 同一台 Windows 主机，JMeter 5.6.3 CLI |
| MySQL | 远端 `192.168.10.102:3306`，单实例 |
| Nacos | 远端 `192.168.10.102:8848`，单实例；namespace `campustrain-dev`，group `CAMPUS_TRAIN` |
| Sentinel Dashboard | `http://192.168.10.102:8858`，运行中，实测 HTTP 200；只观察，不代理业务请求 |
| 软件版本 | Java 17、Spring Boot 4.1.1、Spring Cloud 2025.1.3、Spring Cloud Alibaba 2025.1.0.0、Sentinel 1.8.9 |
| 客户端 | JMeter 5.6.3，60 秒/轮，ramp 5 秒 |

网络关系：JMeter 和 Java 服务在本机，MySQL/Nacos/Dashboard 在 `192.168.10.102`；远端三项均为单实例，未做高可用。**Java 服务之间不是真正的跨机分布式**，三实例也是同机 JVM。

## 2. Topology A：单实例

| 组件 | 实例数 | 端口/地址 | 备注 |
| --- | ---: | --- | --- |
| Gateway | 1 | 8080 | Gateway 入口测试使用 |
| enrollment-service | 1 | 8083 | 直连报名测试使用 |
| training-service | 1 | 8082 | 详情/内部校验直连测试使用 |
| user-service | 1 | 8081 | 报名链 Feign 目标 |
| auth-service | 1 | 8084 | 仅获取 JWT，不计入业务容量 |
| MySQL | 1 | 192.168.10.102:3306 | 共享 |
| Nacos | 1 | 192.168.10.102:8848 | 共享 |
| Sentinel Dashboard | 1 | 192.168.10.102:8858 | 运行中 |

A 的入口标记：

| 资源 | 入口 | 路径 |
| --- | --- | --- |
| training-detail | direct | `http://localhost:8082/trainings/{id}` |
| training-enrollment-check | direct | `http://localhost:8082/internal/trainings/{id}/enrollment-check` |
| enrollment-create | direct | `http://localhost:8083/enrollments` + 身份 Header（仅可信内网实验） |
| gateway-enrollment | gateway | `http://localhost:8080/enrollments` + JWT |

A 的直连报名和 Gateway 报名使用同一份 enrollment/training/user 数据，结果不能混为一条曲线；文档中分别标注。

## 3. Topology B：training 三实例

| 组件 | 实例数 | 端口/地址 | 备注 |
| --- | ---: | --- | --- |
| Gateway | 1 | 8080 | 唯一入口 |
| enrollment-service | 1 | 8083 | 报名链 |
| training-service | 3 | 8082 / 8085 / 8086 | 三个独立 JVM，均注册 Nacos |
| user-service | 1 | 8081 | 报名链 Feign 目标 |
| auth-service | 1 | 8084 | JWT |
| MySQL / Nacos / Dashboard | 1 / 1 / 1 | 192.168.10.102 | 与 A 相同，未扩容 |

路径：

| 资源 | 入口 | 路径 |
| --- | --- | --- |
| training-detail | gateway | `http://localhost:8080/trainings/{id}`，经 `training-service` 路由 + LoadBalancer |
| gateway-enrollment | gateway | `http://localhost:8080/enrollments`，经 `enrollment-service` 路由，再 Feign 到三选一的 training 实例 |

## 4. 1:1:1 分发证据

按同一 60 秒窗口内各实例日志计数：

| 轮次 | 8082 | 8085 | 8086 | 合计 | 偏差 |
| --- | ---: | ---: | ---: | ---: | --- |
| detail 1,000 QPS | 19,630 | 19,631 | 19,631 | 58,892 | 1 条 |
| detail 1,500 QPS | 25,032 | 25,031 | 25,031 | 75,094 | 1 条 |
| enrollment 100 QPS | 2,002 | 2,001 | 2,002 | 6,005 | 1 条 |
| enrollment 200 QPS | 3,920 | 3,919 | 3,919 | 11,758 | 1 条 |

结论：Nacos Discovery 注册和 Spring Cloud LoadBalancer 确实在三个实例间轮询分发，不是“注册了但只打一个实例”。计数来自各实例日志，不是按配置推算。

## 5. 实际结果摘要

| 拓扑 | 资源 | 稳定点 | 拐点/峰值 | 说明 |
| --- | --- | ---: | ---: | --- |
| A | training-detail（direct 8082） | 1,000 QPS，P95 23ms | 拐点 1,100，峰值 1,166 | 单实例 |
| A | training-enrollment-check（direct 8082） | 1,000 QPS，P95 154ms | 拐点 1,200，峰值约 1,151 | 与 detail 同 JVM、不同资源 |
| A | enrollment-create（direct 8083） | 200 QPS，P95 483ms | 拐点 250，平台约 250–300 | 经 user + training Feign |
| A | gateway-enrollment（8080） | 200 QPS，P95 76–402ms | 拐点 300，平台约 300 | 单 Gateway |
| B | training-detail（8080，三实例） | 1,000 QPS，P95 127ms | 拐点 1,500，峰值 1,266 | 1.09× A 直连峰值 |
| B | gateway-enrollment（8080，三实例） | 100 QPS，P95 153ms | 200 QPS P95 820ms | 报名链未改善 |

Topology B 的吞吐没有达到三倍：峰值仅为 A 直连峰值的 1.09 倍、A Gateway 峰值的 1.52 倍。共享 Gateway/MySQL/Nacos 和单机 CPU 是限制因素；不能把 1,266 写成“三倍容量”。

## 6. 未测试拓扑

- Topology C（Gateway + 2/3 enrollment + 3 training）未测试，原因是本次 enrollment 侧在 200–300 QPS 已出现明显尾延迟，但用户要求先完成 A/B 与 Sentinel 对照；没有真实数据，不填 C 的容量。
- Gateway 多实例总入口预算未测试；本次只有一个 Gateway，150 QPS 的 Gateway 规则就是该拓扑的入口总预算，不能外推为多 Gateway 总和。
- 真实跨机部署、容器资源限制、MySQL 高可用均不在本次范围。
