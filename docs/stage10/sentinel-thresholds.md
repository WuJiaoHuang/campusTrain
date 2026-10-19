# Stage 10 Sentinel 最终阈值

状态：已实测并回写 Nacos（2026-10-19）。原始数据见 [benchmark-results.md](benchmark-results.md)，拓扑见 [topology.md](topology.md)。

## 1. 最终规则

| 资源 | 最终阈值 | 生效范围 | 稳定容量（实测） | 拐点（实测） | 安全余量 |
| --- | ---: | --- | ---: | ---: | --- |
| `training-detail` | 800 QPS | training 每 JVM 实例 | 1,000 QPS（994 实际，P95 23ms） | 1,100 QPS（P95 183ms），峰值 1,166 QPS | 比 1,000 低 20% |
| `training-enrollment-check` | 750 QPS | training 每 JVM 实例 | 1,000 QPS（959 实际，P95 154ms） | 1,200 QPS（目标达成 90.9%，P95 264ms） | 比 1,000 低 25% |
| `enrollment-create` | 160 QPS | enrollment 每 JVM 实例 | 200 QPS（198 实际，P95 483ms） | 250 QPS（P95 413–629ms 波动） | 比 200 低 20% |
| `enrollment-service`（Gateway 路由） | 150 QPS | Gateway 每实例入口预算 | 200 QPS（201 实际，P95 76–402ms） | 300 QPS（P95 841ms） | 比 200 低 25% |

四个数字分别来自四条独立曲线，不是复制同一个值，也不是把峰值乘 0.8。稳定容量取“吞吐 ≥ 目标 95%、Error%=0、超时=0、读资源 P95 ≤ 200ms / 报名链 P95 ≤ 500ms”的最后一个点；余量取稳定容量的 20%~30%，没有用峰值。

## 2. Scope：per-instance 还是入口总预算

- `training-detail`、`training-enrollment-check`、`enrollment-create` 是普通 Sentinel FlowRule，规则加载在每个服务 JVM 的本地 `FlowRuleManager`，`count` 是**每实例每秒**预算，不是集群总配额。Nacos 只是共同规则源，不做集群流控。
- `enrollment-service` Gateway 规则是 `resourceMode=0` 的 route 维度 Gateway FlowRule，`count` 是**每个 Gateway 实例**的入口预算。本次只有 1 个 Gateway，所以 150 QPS 同时就是该拓扑的入口总预算。
- 多实例时不能直接写“总 QPS = 阈值 × 实例数”。Topology B 三个 training 实例的 detail 峰值只有 1,266 QPS，远低于 3×800；瓶颈在共享 Gateway/MySQL/主机。只有每个实例都能独立获得流量且共享层不饱和时，才可能接近按实例累加，本实验未证明这一点。
- `training-detail` 与 `training-enrollment-check` 虽然在同一个 training JVM 内是两个独立 Sentinel 资源、Block 互不影响（已做交叉实验），但它们共享进程、连接池和 CPU。800+750 的**并发混合负载没有实测**，不能据此声称单 JVM 可同时承载 1,550 QPS。当前阈值只保证各自单独压测时的边界。

## 3. 选择理由

### training-detail = 800

- 800 QPS 实测 799.2 QPS、P95 22ms、P99 59ms、0 错误；1,000 QPS 仍有 P95 23ms，属于低延迟区间。
- 1,100 QPS 的 P95 突然从 23ms 升到 183ms，吞吐只有 1,063.8，已经不再线性增长；1,200 QPS 峰值 1,165.7 后，1,300 QPS 吞吐跌到 1,065.1。800 留在拐点之前，并比 1,000 稳定点低 20%，可吸收正常波动。

### training-enrollment-check = 750

- 1,000 QPS 实测 958.7、P95 154ms；1,200 QPS 实际只有 1,091.2（目标的 90.9%），P95 264ms，尾延迟已越界；1,400/1,600 目标达成率进一步降到 82.2%/68.3%。
- 该资源的尾延迟波动比 detail 更大，因此取 25% 余量，750 而不是 800。750 也低于 detail 的 800，反映内部校验多了 Feign 链路上下文、但不能据此认为两个资源可以合并额度。

### enrollment-create = 160

- 报名创建每笔要同步调用 user-service 和 training-service，单实例稳定点只有 200 QPS（P95 483ms）；250 QPS 的 P95 在两轮间从 413ms 跳到 629ms，300 QPS 到 803–900ms，400/500 目标下吞吐只有 286/302 QPS。
- 取稳定点 200 的 80%，即 160。该值低于 user-service 停止时的故障流量，保证正常报名不会因为自身限流先失败。

### Gateway enrollment-service = 150

- Gateway 单实例入口 200 QPS 实测 P95 两次为 76ms 和 402ms，已在稳定判定线附近；300 QPS P95 841ms，400/500 QPS 吞吐跌到 278/281，拐点明确。
- 取 25% 余量（200→150），比 enrollment-create 的 160 更保守 10 QPS。这样正常入口流量先在 Gateway 被平滑限制，enrollment-create 的 160 只作为第二层兜底，而不是让 429 主要发生在业务服务内部。Enabled 300 QPS 实验中 Gateway Block 9,135 次、enrollment Block 381 次，验证了这个分层顺序。

## 4. 熔断规则

`training-service-sentinel-degrade-dev.json` 保持两条规则不变：

| 资源 | 策略 | 阈值 | 统计窗口 | 最少请求 | 熔断时长 |
| --- | --- | ---: | ---: | ---: | ---: |
| training-detail | 慢调用比例（RT > 500ms） | 比例 > 0.5 | 10s | 5 | 5s |
| training-detail | 异常比例 | 比例 > 0.5 | 10s | 5 | 5s |

500ms 远高于 800 QPS 时的 P95 22ms，正常流量不会误触发；实测把 `delay-ms` 设为 1500ms 后第 8 个请求被阻断，把异常注入打开后第 6 个请求被阻断，关闭故障后约 8 秒恢复。该规则只作用于 `training-detail`，不影响 `training-enrollment-check`。

## 5. 回写与验证

已写入 Nacos `campustrain-dev / CAMPUS_TRAIN` 并读回校验：

| DataId | 内容 |
| --- | --- |
| `training-service-sentinel-flow-dev.json` | detail=800、check=750 |
| `enrollment-service-sentinel-flow-dev.json` | enrollment-create=160 |
| `campus-gateway-sentinel-flow-dev.json` | route `enrollment-service`=150 |
| `training-service-sentinel-degrade-dev.json` | 两条原始熔断规则，未改 |

本地 `config/nacos/` 同名文件已同步为相同值。最终规则下的对照结果：

| 验证负载 | 阈值生效 | 实测结果 |
| --- | --- | --- |
| detail 900 QPS | 800 | 总吞吐 863.4、成功 777.3/s、429 86.1/s、0×503、0 超时、成功 P95 151ms |
| check 1,200 QPS | 750 | 总吞吐 1,088.6、成功 729.8/s、429 358.7/s、0×503、0 超时、成功 P95 290ms |
| enrollment 300 QPS | 160 | 总吞吐 300.1、成功 159.5/s、429 140.6/s、0×503、0 超时、成功 P95 606ms |
| Gateway 300 QPS | 150 | 总吞吐 301.8、成功 142.1/s、429 159.7/s、0×503、0 超时、成功 P95 498ms |

detail 最终阈值的验证用的是 900 QPS 而不是正好 800 QPS；800 QPS 的 Enabled 单独轮次未跑，800 的依据是未治理基线的 800/1,000 两档加 20% 余量，不额外编造一轮结果。

## 6. 限制

- 所有数值来自单台 Windows 主机 + 远端共享 MySQL/Nacos 的实验室拓扑，不是生产容量承诺。换机器、换网络、换实例数都必须重测。
- Gateway 多实例入口总预算、training 三实例下的 per-instance 拆分比例未做专门实验；不得把 150×N 或 800×3 直接写进生产配置。
- 没有采集 CPU、JVM GC、HikariCP 连接池指标，无法把拐点归因到具体资源；Stage20 再补可观测性。
- 混合流量（detail 与 check 同时打满）、报名真实写入、超卖并发均不在本次阈值验证范围内。
