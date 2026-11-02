# Sentinel 新增资源压测与最终治理

> **数据拓扑说明**：本文件保留两轮结果。Topology A（2 vCPU / 约 3.7 GiB RAM，2026-11-02 00:34–04:55）是资源受限环境下的历史对照；Topology B（4 vCPU / 约 7.6 GiB RAM，2026-11-02 11:25 起）是最终正式基准。Topology A 的原文、表格与结论均未删除、未覆盖；Topology B 从文末「Topology B（4 vCPU / 7.6 GiB）正式基准」开始。最终 Sentinel 阈值以 Topology B 的实测结果为准。

## Topology A 环境与拓扑（历史）

2026-11-02 实测。压测客户端是 Windows 本机的 JMeter 5.6.3 CLI，外部业务入口是 VMware CentOS 虚拟机 `http://192.168.10.102/` 的 Nginx，再到 Gateway。未使用 Windows Docker Desktop。Nacos Discovery 实时查询显示 campus-gateway、auth-service、learning-service、community-service、training-service、enrollment-service、user-service、growth-service 各 1 个健康实例；目标容器内端口分别为 8080、8084、8087、8090、8082、8083、8081、8088。MySQL、Redis、Nacos、RabbitMQ 在同一虚拟机的 Docker Compose 基础设施中；Sentinel Dashboard `http://192.168.10.102:8858/` 实测 HTTP 200。当前是单 Gateway、单业务实例的 smoke 拓扑，不是 Stage10 旧版 Windows 本机多实例拓扑。

Stage A 时 Nacos 新资源 flow 示例文件 `auth-service-sentinel-flow-dev.json`、`learning-service-sentinel-flow-dev.json`、`community-service-sentinel-flow-dev.json` 均保持 `[]`。Stage B 结束后 `auth-service-sentinel-flow-dev.json` 已写入 auth-login=3 QPS；Stage C 结束后 `learning-service-sentinel-flow-dev.json` 已写入 learning-progress-report=8 QPS；Stage D 结束后 `community-service-sentinel-flow-dev.json` 已写入 community-like=8、community-unlike=8 QPS，community 不再为 `[]`。Stage E 结束后 `training-service-sentinel-flow-dev.json` 写入 oss-upload-url=8、oss-play-url=8，`training-service-sentinel-degrade-dev.json` 新增 oss-play-url 异常比例规则；training-detail 800、training-enrollment-check 750、enrollment-create 160、Gateway enrollment route 150 QPS/实例均未改。五份配置均已与 Nacos 读回内容逐字节核对（exactMatch=True），详见下文；最终资源汇总见文末。

## 独立测试数据

已在开发库实际创建 `benchmark_sentinel_student`（userId=1990001，启用 STUDENT）、trainingId=199001（开放报名，容量20，已报名1）、courseId=199001、chapterId=199001、视频 lessonId=199001、questionId=199000001、answerId=199000001，以及一条该学生的有效报名（enrollmentId=6617）。视频课时复用 Stage16 已绑定的真实 OSS 对象，仅用于生成临时播放 URL；没有伪造 objectKey，也没有上传文件。密码通过本机环境变量注入，未写入文档或脚本。数据准备脚本可重复执行，会检查预留 ID、账号和报名人数一致性，不清空任何表。

## 数据安全基线

`scripts/sentinel-benchmark/data/baseline.sql` 只读且每条查询都限定专用 ID；`capture_baseline.py` 可直接输出对应 JSON。成功 smoke 前记录：training capacity20/enrolled_count1，active enrollment1；测试课时持久化进度10秒、completed=0；QUESTION/ANSWER 的 like_count 均0，尚无点赞关系。进度10秒来自首次直接接口排查调用，远低于视频完成阈值，不触发完成事件。smoke 后复核：报名与人数仍为1，进度仍10秒且未完成，两个 like_count 都为0；QUESTION/ANSWER 各有一条 status=0 的取消点赞关系。这是预期的可追踪写入。脚本不执行 TRUNCATE、无条件 DELETE 或全表清理。

## 脚本与 small smoke 结果

运行方法和参数见 [脚本 README](../../scripts/sentinel-benchmark/README.md)。五个 JMX 使用 Stage10 的 ConstantThroughputTimer、CSV JTL 与相同百分位口径；OSS JMX 含上传签名与播放签名两个 sampler。所有请求真实经过 `http://192.168.10.102/`。

| 脚本/资源 | 目标设置 | 实际样本 | HTTP 200 | 错误 | 说明 |
| --- | --- | ---: | ---: | ---: | --- |
| auth-login | 2 QPS，10秒，单线程 | 9 | 9 | 0 | 每次真实登录并断言返回 token；实际约0.915 QPS，低于目标设置值 |
| learning-progress-report | 2 QPS，10秒，单线程 | 14 | 14 | 0 | 固定10秒进度，不触发完成 |
| community-like QUESTION | 1 QPS，5秒 | 1 | 1 | 0 | 点赞专用问题 |
| community-unlike QUESTION | 1 QPS，5秒 | 5 | 5 | 0 | 取消后 like_count 回到0 |
| community-like ANSWER | 1 QPS，5秒 | 5 | 5 | 0 | 点赞专用回答 |
| community-unlike ANSWER | 1 QPS，5秒 | 5 | 5 | 0 | 取消后 like_count 回到0 |
| oss-upload-url + oss-play-url | 每个组 1 QPS，10秒 | 9 | 9 | 0 | 只生成签名 URL，未上传视频 |

所有成功轮次均无 404/405。初次 learning-progress 用 4 线程、15秒响应超时运行时有 4 个 SocketTimeoutException；直连同接口 238ms 返回200，调整为30秒超时并用单线程小流量重跑后 14/14 成功。保留此记录以免把初次失败误记为通过。auth 首轮2线程只有2次成功且延迟较高，单线程重跑后9/9成功；2 QPS 是设置值，实际约0.915 QPS，Stage A 不将其视为容量数据。原始 JTL/summary 保存在本机被忽略的 `scripts/sentinel-benchmark/results/`，不会提交 Git。

## 结果口径与后续

`summarize.py` 读取 JTL CSV，计算 samples、实际 QPS、平均、P90/P95/P99、最大值、Error%、200/400/429/500/503、timeout 与 connectionError，另外列出 404/405 和各 sampler 明细。实际 QPS 是样本数除以首个请求开始至末个请求完成的时间窗。目标 QPS 由 JMeter timer 控制，短时 smoke 不用于阈值推断。

## 容量结果（Stage B：auth-login）

### B.1 环境与口径

2026-11-02 00:34–01:12 实测。入口固定为 `POST http://192.168.10.102/auth/login`，链路 Nginx → Gateway → auth-service，单 auth 实例。请求携带 Stage A 基准账号和密码，密码只通过本进程环境变量注入 JMeter，不写入脚本、文档或 Git；服务端执行真实 BCrypt（数据库密码哈希成本因子 `$2b$10$`），不是 mock。压测前已读回 Nacos `campustrain-dev / CAMPUS_TRAIN / auth-service-sentinel-flow-dev.json` 为 `[]`，裸容量阶段没有流控。

JMeter 5.6.3 CLI，每轮 60 秒、ramp 5 秒，ConstantThroughputTimer 控制目标；实际 QPS 按 JTL 首请求开始至末请求完成的时间窗计算。所有百分位由 JTL 全量样本 nearest-rank 重算。warm-up 5 QPS / 15 秒不计入结论。稳定性判据：实际吞吐 ≥ 目标 95%、Error%=0、无 5xx/超时/连接错误、P95 不明显失控、CPU 不持续饱和；auth 额外观察 P95 ≤ 500ms 与 BCrypt 饱和。

虚拟机为 2 vCPU、约 3.7 GiB RAM，14 个容器全量运行；压测前后空载基线机器 CPU 约 46%–70%、load 5–10，内存已用约 3.6 GiB，swap 约 2.2 GiB。auth 容器无 CPU 配额限制。该共享基线是解释本轮尾延迟的必要背景。

### B.2 未治理裸容量

| 目标 QPS | 线程 | 样本 | 实际 QPS | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 / 429 / 503 / timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 2 | 61 | 1.03 | 498 | 813 | 855 | 1,430 | 1,430 | 0 | 61 / 0 / 0 / 0 |
| 2 | 10 | 124 | 2.10 | 876 | 1,832 | 2,177 | 2,625 | 2,842 | 0 | 124 / 0 / 0 / 0 |
| 2 | 4 | 115 | 1.90 | 898 | 1,708 | 2,500 | 4,150 | 4,290 | 0 | 115 / 0 / 0 / 0 |
| 3 | 6 | 131 | 1.96 | 1,968 | 5,499 | 7,096 | 17,299 | 17,429 | 0 | 131 / 0 / 0 / 0 |
| 3 | 6 | 134 | 2.21 | 2,057 | 3,757 | 4,783 | 8,871 | 9,230 | 0 | 134 / 0 / 0 / 0 |
| 3 | 6 | 133 | 1.95 | 1,624 | 4,259 | 11,616 | 16,916 | 17,275 | 0 | 133 / 0 / 0 / 0 |
| 4 | 20 | 244 | 4.04 | 2,354 | 4,315 | 5,169 | 6,251 | 7,812 | 0 | 244 / 0 / 0 / 0 |
| 5 | 20 | 267 | 4.36 | 3,728 | 5,864 | 6,317 | 7,178 | 7,522 | 0 | 267 / 0 / 0 / 0 |
| 6 | 30 | 283 | 4.03 | 6,884 | 11,612 | 12,906 | 15,373 | 15,541 | 0 | 283 / 0 / 0 / 0 |
| 10 | 50 | 236 | 3.41 | 13,387 | 17,167 | 18,159 | 19,653 | 20,754 | 0 | 236 / 0 / 0 / 0 |

3 QPS 用相同参数重复三次，实际吞吐 1.95–2.21 QPS、P95 4.8–11.6 秒，方差明显；没有只保留较好的一轮。目标 5 QPS 时实际 4.36 QPS（87.2%），已是观测到的吞吐峰值；6 QPS 反而降到 4.03，10 QPS 降到 3.41。串行单请求实测 223–511ms，说明即使无并发，BCrypt 在共享 VM 上也有数百毫秒。

CPU 采样（每 5 秒，去掉首末各 1–2 个采样点）：

| 目标 QPS | 机器 CPU 均值（峰值） | auth 容器 CPU 均值（峰值，单核百分比） | 观察 |
| ---: | ---: | ---: | --- |
| 1 | 67.5%（82.8%） | 25.4%（65.7%） | 未饱和，吞吐达标 |
| 2 | 81.1%（99.5%） | 46.4%（95.7%） | 机器接近饱和 |
| 3 | 89.3%（100%） | 46.4%（91.0%） | 机器多次 100% |
| 5 | 91.5%（100%） | 65.0%（121.8%） | auth 短时用满两核，机器饱和 |
| 10 | 97.3%（100%） | 54.2%（113.0%） | 饱和后吞吐下降，CPU 时间耗在排队 |

**结论**：最后稳定点是 4 QPS（实际 4.041，为目标 101%，Error=0）；拐点在 4 与 5 之间，5 QPS 目标只完成 87.2%；峰值 4.36 QPS；6/10 QPS 已低于峰值，属于过载区。瓶颈是 auth 容器 BCrypt 与整机 CPU：机器在 3 QPS 起持续 89%–100%，内存与 swap 压力放大了尾延迟。

**P95 ≤ 500ms 未达成**：最低档 1 QPS 的 P95 已是 855ms，串行单请求也有 223–511ms。原因是 2 vCPU 上同时运行 14 个容器，空载基线已占约一半 CPU，且 3.6 GiB 内存接近用满、swap 约 2.2 GiB；不是 auth 代码或流控造成。因此本轮不能宣称满足 500ms 尾延迟，阈值只能按吞吐拐点和 CPU 余量确定。这一限制会保留到最终报告，不用未实测的数掩盖。

### B.3 最终阈值

**auth-login = 3 QPS/实例**。理由：最后稳定点 4 QPS，阈值取其 75%（4 → 3），低于 4–5 之间的拐点，也低于峰值 4.36 约 31%；3 QPS 下 auth 具备约 1.4 QPS 的突发余量，整机仍可留出非 auth 服务所需 CPU。没有取 4（贴着拐点、无余量），也没有取 5（已过拐点）。阈值是每 auth JVM 的本地 FlowRule，不是集群总配额。

已写入 Nacos `campustrain-dev`、group `CAMPUS_TRAIN`、DataId `auth-service-sentinel-flow-dev.json`，并读回校验；本地 `config/nacos/auth-service-sentinel-flow-dev.json` 同步为相同内容：

```json
[
  {"resource":"auth-login","limitApp":"default","grade":1,"count":3,"strategy":0,"controlBehavior":0}
]
```

### B.4 开启 Sentinel 后复测

| 负载 | 线程 | 样本 | 实际总 QPS | 200 | 429 | 429 占比 | 成功 QPS | 成功 P95 ms | 总 P95 ms | 503 | timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 2（0.75T 以下） | 4 | 118 | 1.99 | 111 | 7 | 5.9% | 1.87 | 2,319 | 2,319 | 0 | 0 |
| 2（0.75T 以下） | 15 | 130 | 2.18 | 116 | 14 | 10.8% | 1.95 | 1,416 | 1,460 | 0 | 0 |
| 3（1.0T） | 8 | 185 | 3.12 | 157 | 28 | 15.1% | 2.65 | 1,453 | 1,334 | 0 | 0 |
| 5（约 1.7T） | 12 | 307 | 5.14 | 159 | 148 | 48.2% | 2.66 | 1,321 | 968 | 0 | 0 |
| 6（2.0T） | 15 | 368 | 6.03 | 167 | 201 | 54.6% | 2.74 | 1,484 | 1,103 | 0 | 0 |

阈值上方负载主要被 429 拒绝，成功吞吐稳定在约 2.7 QPS；全程 0 个 503、0 超时、0 连接拒绝/重置，服务健康。成功 P95 约 1.3–2.3 秒，未达到 500ms，原因同上，不是限流导致。

阈值下方仍出现 5.9%–10.8% 的 429：Sentinel QPS 规则按 1 秒窗口计数，目标 2 QPS 的到达并非严格均匀，1 秒内瞬时超过 3 个请求就会被拒绝；这是低 QPS 下每秒计数流控的固有突发效应，不是阈值失效。3 QPS（正好等于阈值）时 15.1% 的 429 也来自同样的秒内突发。文档保留这些数字，不把 429 全部归因于过载。

### B.5 健康与回退

复测后 `docker compose ps` 全部服务 healthy，auth 登录仍返回 200。若需回退，将 Nacos 与本地文件恢复为 `[]` 即关闭流控；本次未改其它资源、未改旧阈值、未改业务代码。原始 JTL、summary 与 CPU CSV 保存在被 Git 忽略的 `scripts/sentinel-benchmark/results/`，不提交。

### B.6 限制

- 2 vCPU / 3.7 GiB 共享 VM 的绝对容量不能外推到生产；换机器、换实例数必须重测。
- auth 容量完全由 BCrypt 与整机 CPU 决定，本轮无法给出 500ms 尾延迟承诺。
- 5/6/10 QPS 已进入过载区，未继续跑 20–100 QPS：吞吐平台和回落在 6–10 QPS 已明确，继续加压只会制造客户端超时，不会产生新的容量信息。
- 单实例结果不代表多实例总容量；阈值是每实例本地规则。

## 容量结果（Stage C：learning-progress-report）

### C.1 环境、数据与口径

2026-11-02 01:16–02:00 实测。入口固定为 `POST http://192.168.10.102/learning/report-progress/{lessonId}`，链路 Nginx → Gateway → learning-service（单实例）。请求先经 Feign 调用 training-service 查询课时、调用 enrollment-service 校验有效报名，再进入 Redis Lua 单调更新与 dirty 集合，由每 5 秒、批量 100 的落库任务写入 `campus_learning.learning_record`。learning-service 的 `/internal/learning/**` 内部接口没有 Sentinel 资源，Feign 调用不参与本资源的流控。

使用基准学生 1990100–1991099（共 1,000 人，均对 trainingId=199001 有有效报名）和专用视频课时 199002–199080。每个用户由 JMeter JSR223 生成按请求次数递增的 `PROGRESS_SECONDS`，请求路径是真实的“进度变大”更新，不是旧进度被忽略的快路径。为制造重复更新，每轮只从令牌池取前 100 个用户，60 秒内每个用户约更新 3–14 次；各轮分别使用全新课时（199011–199017、199021–199024），最终进度 3–8 秒，远低于 90 秒完成阈值，因此 completed 始终为 0，不触发完成事件。正式测试前已读回 Nacos `campustrain-dev / CAMPUS_TRAIN / learning-service-sentinel-flow-dev.json` 为 `[]`。

warm-up 10 QPS / 10 线程只完成 1.97 QPS（线程不足），不计入结论。稳定性判据：实际吞吐 ≥ 目标 95%、Error%=0、无 5xx/超时/连接错误、P95 不明显失控、CPU 不持续饱和；同时观察 Redis/Lua 错误、dirty 集合、落库追赶与 Feign 错误。

### C.2 未治理裸容量

| 目标 QPS | 线程 | 用户池 | 课时 | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 / 429 / 503 / timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 5 | 20 | 1,000 | 199007 | 299 | 4.906 | 98.1% | 1,924 | 3,084 | 5,243 | 6,471 | 7,190 | 0 | 299 / 0 / 0 / 0 |
| 10 | 25 | 1,000 | 199008 | 458 | 7.626 | 76.3% | 3,071 | 4,201 | 4,573 | 6,466 | 8,901 | 0 | 458 / 0 / 0 / 0 |
| 20 | 50 | 1,000 | 199009 | 540 | 8.474 | 42.4% | 5,503 | 8,337 | 8,847 | 9,681 | 10,277 | 0 | 540 / 0 / 0 / 0 |
| 30 | 100 | 1,000 | 199010 | 542 | 8.390 | 28.0% | 11,286 | 16,527 | 18,298 | 21,929 | 22,760 | 0 | 542 / 0 / 0 / 0 |
| 5 | 20 | 100 | 199011 | 310 | 5.192 | 103.8% | 829 | 2,074 | 2,486 | 3,042 | 3,919 | 0 | 310 / 0 / 0 / 0 |
| 10 | 30 | 100 | 199012 | 605 | 10.017 | 100.2% | 874 | 1,749 | 1,985 | 2,448 | 3,279 | 0 | 605 / 0 / 0 / 0 |
| 15 | 80 | 100 | 199015 | 415 | 5.527 | 36.8% | 12,325 | 23,343 | 25,654 | 29,026 | 30,015 | 0.482 | 413 / 0 / 0 / 2 |
| 15 | 80 | 100 | 199016 | 856 | 13.910 | 92.7% | 3,641 | 6,689 | 7,509 | 8,865 | 10,271 | 0 | 856 / 0 / 0 / 0 |
| 20 | 60 | 100 | 199013 | 700 | 11.252 | 56.3% | 4,990 | 7,930 | 8,997 | 10,601 | 14,146 | 0 | 700 / 0 / 0 / 0 |
| 20 | 100 | 100 | 199014 | 749 | 11.550 | 57.8% | 7,905 | 12,600 | 13,945 | 16,180 | 17,767 | 0 | 749 / 0 / 0 / 0 |

1,000 用户轮次每轮只取 1,000 个令牌、每用户大多只上报一次；Redis 进度键首次访问需要从 MySQL 初始化，且这些轮次的线程数偏低，因此吞吐明显低于同目标值的 100 用户热键轮次。100 用户轮次才是重复更新主路径的数据。15 QPS 第一轮（lesson 199015）在 01:43–01:44 遇到 Nacos gRPC 健康检查失败，吞吐跌到 5.527 QPS 并出现 2 个 SocketTimeoutException；服务在 01:44:36 自动恢复，同参数重跑（199016）得到 13.910 QPS、0 错误。第一轮作为异常保留，不用于容量结论。

**结论**：最后完全稳定点是 10 QPS（实际 10.017，为目标 100.2%，Error=0，P95 1,985ms）；拐点在 10 与 15–20 QPS 之间，15 QPS 重跑为目标的 92.7%，20 QPS 两次只有 56.3%–57.8%；已观测吞吐峰值为 13.91 QPS。机器 CPU 在 10 QPS 起均值约 88%、峰值 100%，20–30 QPS 时均值 92%–100%，瓶颈是 2 vCPU 共享 VM 的整机 CPU 与内存/swap 压力，不是 Lua 或落库错误。

CPU 采样（每 5 秒，去掉首末各 1–2 个采样点）：

| 目标 QPS | 用户池 | 机器 CPU 均值（峰值） | 观察 |
| ---: | ---: | ---: | --- |
| 5 | 1,000 | 96.1%（100%） | 空载基线高，已接近饱和 |
| 10 | 1,000 | 96.1%（100%） | 吞吐只完成 76.3% |
| 20 | 1,000 | 99.6%（100%） | 持续饱和 |
| 30 | 1,000 | 98.0%（100%） | 过载，吞吐低于峰值 |
| 5 | 100 | 86.1%（100%） | 热键路径，吞吐达标 |
| 10 | 100 | 88.1%（100%） | 本轮最佳稳定点 |
| 15 | 100 | 88.5%（100%） | 重跑值；首轮因 Nacos 异常为 100% |
| 20 | 100 | 92.2%–93.6%（100%） | 两次均未达标 |

压测期间内存已用约 3.6 GiB，swap 约 2.2–2.4 GiB；14 个容器同时运行，空载机器 CPU 已占约一半。该基线解释了 10 QPS 时 P95 已接近 2 秒、15–20 QPS 时尾延迟达到 7–14 秒。

### C.3 Redis、落库与一致性观测

- 未治理各轮均无 Lua 错误、Redis 连接错误或 Feign 调用错误；learning 日志中没有 Redis/Lua/Feign 异常，错误仅来自上述 Nacos 连接事件。
- dirty 集合在轮次中增长，结束后可回落到 0；观测到 237 → 0 约 40 秒完成。40 分钟窗口内落库任务共执行 83 批、写入 4,533 条，单批上限 100，符合每 5 秒批量 100 的配置；查询时 MySQL 已追平 Redis。
- 未治理轮次结束后核对 lessons 199011–199014：400 个用户/课时组合在 Redis 与 MySQL 的 `progress_seconds`、`completed` 完全一致（400/400，0 不一致），completed 全为 0。
- 完成事件表 `campus_learning.event_outbox` 总计 6 行，均为历史数据（用户 1001/1004，课时 16001/16002/16003/5001，2026-10-26 至 11-01），没有本次基准用户或 benchmark 课时产生的新事件。

### C.4 最终阈值

**learning-progress-report = 8 QPS/实例**。理由：最后完全稳定点 10 QPS，阈值取 80%（10 → 8），低于 10–15 之间的拐点，也明显低于已观测峰值 13.91 QPS；10 QPS 时整机 CPU 已达 88%，P95 约 2 秒，15–20 QPS 尾延迟和吞吐同时恶化，因此不取 10（无余量）也不取 12（已过拐点）。8 QPS 为共享 VM 上的 GC、Nacos 心跳和落库任务保留余量。阈值是每 learning JVM 的本地 FlowRule，不是集群总配额。

已写入 Nacos `campustrain-dev`、group `CAMPUS_TRAIN`、DataId `learning-service-sentinel-flow-dev.json`，并读回校验；本地 `config/nacos/learning-service-sentinel-flow-dev.json` 同步为相同内容：

```json
[
  {"resource":"learning-progress-report","limitApp":"default","grade":1,"count":8,"strategy":0,"controlBehavior":0}
]
```

### C.5 开启 Sentinel 后复测

| 负载 | 线程 | 样本 | 实际总 QPS | 200 | 429 | 429 占比 | 成功 QPS | 成功 P95 ms | 总 P95 ms | 503 | timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 6（0.75T） | 12 | 366 | 6.16 | 345 | 21 | 5.7% | 5.806 | 916 | 896 | 0 | 0 |
| 6（0.75T，突发对照） | 40 | 376 | 6.322 | 275 | 101 | 26.9% | 4.623 | 4,176 | 4,810 | 0 | 0 |
| 8（1.0T） | 16 | 485 | 8.147 | 388 | 97 | 20.0% | 6.518 | 1,799 | 1,668 | 0 | 0 |
| 12（1.5T） | 24 | 708 | 11.929 | 420 | 288 | 40.7% | 7.077 | 2,736 | 2,601 | 0 | 0 |
| 16（2.0T） | 32 | 965 | 16.138 | 465 | 500 | 51.8% | 7.797 | 2,467 | 1,660 | 0 | 0 |

阈值上方负载主要被 429 拒绝，成功吞吐稳定在约 7.1–7.8 QPS；全程 0 个 503、0 超时、0 连接拒绝/重置，服务健康。6 QPS / 40 线程那一轮是因客户端排队后集中释放造成的突发对照：大量请求在同一秒内到达，触发秒级窗口拒绝（26.9%），不是阈值失效；同目标 12 线程时 429 降到 5.7%，说明客户端并发与到达平滑度会显著影响低负载下的 429 比例。

阈值附近仍有 20% 的 429：Sentinel QPS 规则按 1 秒窗口计数，JMeter 的 ConstantThroughputTimer 在响应变慢时会形成秒内突发，8 QPS 目标下单秒可超过 8 个请求；这是秒级计数的固有突发效应。阈值上方成功吞吐略低于 8，也是同一原因造成的窗口利用率损失。文档保留这些数字，不把 429 全部归因于过载。

### C.6 数据一致性与健康检查

复测结束等待 dirty 集合归零后核对 lessons 199017、199021–199024：500 个用户/课时组合在 Redis 与 MySQL 的进度和 completed 完全一致（500/500，0 不一致），completed 全为 0，没有提前完成或进度回退；`campus_learning.event_outbox` 仍为 6 行历史数据，没有新增完成事件。dirty 集合最终为 0。全部 14 个容器 `docker ps` 为 healthy，learning-service 容器未重启；Nacos HTTP 200，服务在 Nacos 异常后自动恢复。

### C.7 异常、回退与限制

- 01:43–01:44 的 Nacos gRPC 异常使 15 QPS 第一轮出现 2 个客户端超时和 5.527 QPS 的低值，日志为 `Server check fail, please check server nacos, port 9848` 与 `Client not connected, current status:UNHEALTHY`；01:44:36 恢复推送并重新收到服务列表。根因是 2 vCPU 整机在压测中持续 100% CPU、swap 约 2.4 GiB，Nacos 客户端健康检查超时；不是 learning 业务代码错误，也不属于 Sentinel 限流。该轮保留在表中，不用重跑值覆盖。
- 2 vCPU / 3.7 GiB 共享 VM 的绝对容量不能外推到生产；换机器、换实例数必须重测。
- 单实例结果不代表多实例总容量；阈值是每实例本地规则。
- 若需回退，将 Nacos 与本地文件恢复为 `[]` 即关闭流控；本次未改其它资源、未改旧阈值、未改业务代码。原始 JTL、summary 与 CPU CSV 保存在被 Git 忽略的 `scripts/sentinel-benchmark/results/`，不提交。

## Stage D：community-like / community-unlike

2026-11-02 02:30–03:10 实测。入口为 `POST /community/like` 和 `POST /community/unlike`，经 Nginx、Gateway 到单实例 community-service（容器内 8090）。点赞与取消点赞分开测试；每轮前后读取 Redis 的 users/count/revision/init/dirty 和 MySQL 的 like_count、有效/取消关系数。D 阶段只接入并启用两条 Sentinel 规则，不改业务代码。

### D.1 基准数据、令牌与口径

- 社区基准用户为 `benchmark_comm_00001`–`benchmark_comm_06000`（userId 1992001–1998000）。只把这些专用用户的 BCrypt 哈希更新为进程内存中的随机口令（cost 10），再通过真实 `/auth/login` 获取 6,000 个 JWT，耗时约 1,131 秒；口令和 JWT 均未落盘或提交，令牌 CSV 位于被 Git 忽略的 `scripts/sentinel-benchmark/results/tokens/community-tokens.csv`。
- 6,000 次登录会受 auth-login=3 QPS 限制，生成令牌期间临时移除该流控，完成后立即恢复为 3 QPS 并从 Nacos 读回确认；临时关闭仅用于准备令牌，不作为 auth-login 的压测结果。
- 专用目标如下。点赞主容量使用不同用户的首次点赞；取消点赞只使用此前已点赞的用户，不用从未点赞的用户刷 `changed=false` 快路径。

| 目标 | ID | 用途 | 说明 |
| --- | --- | --- | --- |
| QUESTION | 199000002 | Q2 点赞/取消主容量 | 预初始化用户 1992001，避免 Redisson 首次初始化锁的冷启动 |
| ANSWER | 199000002 | A2 幂等验证 | 同一用户连续点赞、连续取消 |
| QUESTION | 199000003 | Q3 warm-up | 只用于预热，不纳入容量结论 |
| QUESTION | 199000004 | Q4 50 QPS 点赞、50 QPS 取消 | 全新目标，预初始化用户 1992001 |
| QUESTION | 199000005 | Q5 启用 Sentinel 后复测 | 全新目标，预初始化用户 1992001 |

- Q2 的 10/20/30 QPS 点赞轮使用互不重叠的令牌区间，确保绝大多数请求是首次点赞。10 QPS 轮 607 个样本中有 7 个因令牌回收重复点赞（1.15%），均返回 `changed=false`，没有计数膨胀；20 QPS 两轮和 30 QPS 轮没有重复点赞主导。50 QPS 轮改用全新的 Q4，避免 Q2 已点赞用户影响结果。
- 取消点赞轮从 Q2/Q4 的已点赞集合中取令牌，并以实际计数下降核对有效取消。20 QPS 首轮曾混入前序点赞轮未消费的令牌，1,167 个样本中只有 840 次有效取消，327 次是 `changed=false`；该轮标记为污染轮，不用于容量结论，换用确定已点赞的 D 区间重跑。
- 指标口径与 Stage B/C 相同：实际 QPS = 样本数 / JTL 实际时间窗；P90/P95/P99 为全量样本 nearest-rank；Error% 包含 429 和 503；成功 QPS 只统计 HTTP 200。CPU/load 由虚拟机 `/proc/stat` 和 `/proc/loadavg` 采样，不使用 Prometheus。

### D.2 点赞裸容量（Sentinel flow 关闭）

| 目标 QPS | 线程 | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 | 429 | 503 | 成功 QPS |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 20 | 607 | 10.236 | 102.4% | 274 | 772 | 993 | 1,344 | 1,766 | 0 | 607 | 0 | 0 | 10.236 |
| 20（首轮） | 30 | 930 | 13.996 | 70.0% | 1,351 | 2,580 | 4,063 | 14,612 | 14,966 | 4.839% | 885 | 0 | 45 | 13.364 |
| 20（重跑） | 30 | 1,148 | 19.331 | 96.7% | 471 | 986 | 1,263 | 4,168 | 5,204 | 0.087% | 1,147 | 0 | 1 | 19.315 |
| 30 | 60 | 1,739 | 29.070 | 96.9% | 1,253 | 2,399 | 2,629 | 3,132 | 3,839 | 0 | 1,739 | 0 | 0 | 29.070 |
| 50 | 100 | 1,496 | 24.021 | 48.0% | 3,859 | 7,339 | 8,760 | 15,325 | 18,167 | 14.84% | 1,274 | 0 | 222 | 20.457 |

10 QPS 是最后完全稳定点：0 个 503、P95 993 ms，整机 CPU 平均 75.3%、峰值 100%，load 最高 11.41。20 QPS 首轮在 02:34:21–02:35:08 出现 45 个 503，全部是 `QueryTimeoutException: Redis command timed out`；Redis slowlog 记录到 flush 锁 `EVALSHA` 59 秒、`INFO` 22.6 秒、`GET` 13.9 秒、`SCRIPT LOAD` 19 秒，整机 CPU 平均 85.8%、峰值 99.9%，load 最高 13.00。该轮实际只完成 13.996 QPS，900 次点赞已生效（其中 15 个 503 在 Lua 成功后返回），Q2 从 601 增至 1,501，数据没有丢失；失败轮保留，不隐藏。

20 QPS 重跑提升到 19.331 QPS，仍有 1 个 Redis 超时 503（02:39:03），Q2 从 1,501 增至 2,649，与 1,148 次受理一致。该轮没有采集到机器 CPU，原因是后台统计进程未拿到 `VM_PASSWORD` 而退出；不补造数据，CPU 证据使用首轮 85.8%/99.9% 和 30 QPS 轮 87.6%/100% 作为区间参考。30 QPS 虽然 0 错误、实际 29.070 QPS，但 CPU 平均 87.6%、多数采样接近 100%，load 最高 20.93，P95 已到 2.6 秒，不能算稳定点。50 QPS 明显崩溃：实际 24.021 QPS、222 个 503（14.84%）、成功吞吐 20.457 QPS、P95 8.76 秒，CPU 平均 90.8%、峰值 100%，load 最高 23.76。点赞拐点在 20–30 QPS，峰值约 29 QPS；瓶颈是共享 2 vCPU、Redis 与 flush 的整机调度，不是 Lua 业务错误。

### D.3 community-like 阈值

**community-like = 8 QPS/实例**。最后完全稳定点是 10 QPS，取 80% 得 8 QPS；10 QPS 时整机 CPU 已平均 75.3%、峰值 100%，20 QPS 重跑仍有 Redis 超时，30 QPS 虽然零错误但 CPU 长期接近打满且 P95 2.6 秒，50 QPS 出现 14.84% 的 503。8 QPS 为同一 VM 上的 flush、Nacos、Gateway 和其它业务实例保留余量，也与 learning-progress-report 的“稳定点 80%”取法一致。该值是每个 community JVM 的本地 FlowRule，不是集群总量。

### D.4 幂等与取消点赞裸容量

同一用户对 A2 连续两次点赞：第一次 `changed=true`，计数 0→1、users 集合 1 个成员；第二次 `changed=false`，计数仍为 1、users 仍为 1。随后连续两次取消：第一次 1→0，第二次 `changed=false`，计数保持 0，没有出现负数。最终 Redis count=0、users=0、revision=6，MySQL `like_count`=0、有效关系 0、取消关系 1。重复请求只返回幂等结果，不产生重复有效关系。

| 目标 QPS | 线程 | 时长 s | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 | 429 | 503 | 成功 QPS |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 10 | 20 | 59.3 | 611 | 10.300 | 103.0% | 165 | 309 | 553 | 1,285 | 1,485 | 0 | 611 | 0 | 0 | 10.300 |
| 20（污染轮） | 30 | 59.7 | 1,167 | 19.555 | 97.8% | 417 | 938 | 1,283 | 3,367 | 3,693 | 0 | 1,167 | 0 | 0 | 19.555 |
| 20（重跑） | 30 | 60.4 | 1,190 | 19.702 | 98.5% | 283 | 533 | 1,043 | 2,717 | 2,983 | 0 | 1,190 | 0 | 0 | 19.702 |
| 30 | 40 | 34.7 | 1,023 | 29.464 | 98.2% | 531 | 1,099 | 1,348 | 1,700 | 2,019 | 0 | 1,023 | 0 | 0 | 29.464 |
| 50 | 80 | 25.6 | 927 | 36.221 | 72.4% | 1,921 | 3,338 | 3,639 | 4,483 | 5,276 | 0 | 927 | 0 | 0 | 36.221 |

10 QPS 轮 611 次全部有效，Q2 从 4,388 精确降到 3,777；20 QPS 重跑 1,190 次全部有效，Q2 从 2,937 降到 1,747；30 QPS 轮 1,023 次全部有效，Q2 从 1,747 降到 724。30 QPS 轮只跑 34.7 秒，是为了在 Q2 已点赞池耗尽前完成；50 QPS 轮只跑 25.6 秒，是在 Q4 的 1,394 个已点赞用户内完成，避免退化成未点赞快路径。50 QPS 虽然 0 错误，但只达到目标的 72.4%，P95 3.64 秒，CPU 平均 85.0%、峰值 100%，load 最高 18.55，吞吐平台约 36.2 QPS。取消点赞的 10/20/30 QPS 均无 5xx、无超时，稳定点至少到 30 QPS；50 QPS 是明显拐点，峰值约 36.2 QPS，但 30 QPS 已把 CPU 推到高位。

**community-unlike = 8 QPS/实例**。取消点赞裸容量在 30 QPS 仍无错误，但它与点赞共享 JVM、Redis、Lua、dirty 集合和 MySQL flush 路径；若取消点赞单独放宽到 20–30 QPS，会与点赞流量争抢同一 Redis/连接池并放大超时。按点赞稳定点 10 QPS 的 80% 统一取 8 QPS，降低两条写路径互相挤压的风险；这是保护阈值，不是取消点赞的最大能力。最终 Nacos 与 Git 文件同时包含两条规则：

```json
[
  {"resource":"community-like","limitApp":"default","grade":1,"count":8,"strategy":0,"controlBehavior":0},
  {"resource":"community-unlike","limitApp":"default","grade":1,"count":8,"strategy":0,"controlBehavior":0}
]
```

规则通过 Nacos v1 API 发布到 namespace `campustrain-dev`、group `CAMPUS_TRAIN`、DataId `community-service-sentinel-flow-dev.json`，发布和读回内容一致；community-service 在 02:54:52 收到 `ConfigChangeNotifyRequest`，02:54:53 完成 `NacosDataSource` 刷新。本地 `config/nacos/community-service-sentinel-flow-dev.json` 与 Nacos 内容一致。

### D.5 开启 Sentinel 后点赞复测

| 负载 | 线程 | 样本 | 实际 QPS | 200 | 429 | 429 占比 | 成功 QPS | 总 P95 ms | 成功 P95 ms | 503 | timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 6（0.75T，20 线程） | 20 | 374 | 6.307 | 329 | 45 | 12.03% | 5.548 | 337 | 347 | 0 | 0 |
| 6（0.75T，6 线程） | 6 | 361 | 6.093 | 360 | 1 | 0.28% | 6.076 | 234 | 234 | 0 | 0 |
| 8（1.0T） | 8 | 453 | 7.640 | 433 | 20 | 4.42% | 7.303 | 802 | 916 | 0 | 0 |
| 12（1.5T） | 12 | 702 | 11.830 | 466 | 236 | 33.62% | 7.853 | 967 | 1,223 | 0 | 0 |
| 16（2.0T） | 16 | 939 | 15.831 | 475 | 464 | 49.41% | 8.014 | 981 | 1,300 | 0 | 0 |

所有 429 的 `blockType` 都是 `FlowException`，返回 429；全程 0 个 503、0 超时、0 连接拒绝/重置。8 QPS 规则下成功吞吐封顶在约 7.3–8.0 QPS，12 和 16 QPS 的额外流量主要被 429 拒绝，服务保持健康。6 QPS / 20 线程出现 45 个 429（12.03%），同一目标改用 6 线程后只剩 1 个（0.28%），说明是客户端排队后的秒内突发，不是规则误限；该对照轮保留。

### D.6 开启 Sentinel 后取消点赞复测

| 负载 | 线程 | 样本 | 实际 QPS | 200 | 429 | 429 占比 | 成功 QPS | 总 P95 ms | 成功 P95 ms | 503 | timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 6（0.75T） | 6 | 361 | 6.090 | 361 | 0 | 0 | 6.090 | 201 | 201 | 0 | 0 |
| 8（1.0T） | 8 | 471 | 7.946 | 458 | 13 | 2.76% | 7.727 | 1,069 | 1,070 | 0 | 0 |
| 12（1.5T） | 12 | 721 | 12.159 | 473 | 248 | 34.40% | 7.977 | 388 | 519 | 0 | 0 |
| 16（2.0T） | 16 | 957 | 16.134 | 476 | 481 | 50.26% | 8.057 | 305 | 551 | 0 | 0 |

取消点赞的成功吞吐同样封顶在约 8.0–8.1 QPS，超额流量为 429，0 个 503、0 超时。12 QPS 轮有 721 个样本但只有 720 个令牌，JMeter 回收令牌造成 1 次重复取消（`changed=false`）；这使 Q5 最终有效数比“每个令牌只取消一次”的预期多 1，属于测试令牌复用，不是计数错误，保留并在此说明。

### D.7 最终一致性与健康检查

等待全部 dirty 刷完后核对专用目标：

| 目标 | Redis count | Redis users | Redis revision | MySQL like_count | 有效关系 | 取消关系 | dirty |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Q2 199000002 | 724 | 724 | 8,052 | 724 | 724 | 3,664 | 0 |
| Q3 199000003 | 45 | 45 | 45 | 45 | 45 | 0 | 0 |
| Q4 199000004 | 467 | 467 | 2,321 | 467 | 467 | 927 | 0 |
| Q5 199000005 | 297 | 297 | 3,831 | 297 | 297 | 1,767 | 0 |
| A2 199000002 | 0 | 0 | 6 | 0 | 0 | 1 | 0 |

每个目标都满足 Redis count = Redis users = MySQL like_count = 有效关系数；没有重复有效关系，没有 0/1 之外的 status，没有负 like_count，dirty 集合为 0。点赞裸容量首轮和 50 QPS 轮的 503 没有留下脏数据：Q2 与 Q4 的最终计数都由 Redis users 集合驱动并在 flush 后对齐 MySQL。过去 1 小时 community 日志有 269 条 `like Redis unavailable` 和 1 条 `like flush failed`，对应 1 条 warm-up、45 条 20 QPS 首轮、1 条 20 QPS 重跑和 222 条 50 QPS 点赞，全部发生在流控关闭的裸容量/预热轮次；启用规则后的复测 0 个 503，最近 15 分钟对应日志为 0。community-service 容器重启次数为 0，14 个容器均为 healthy，Sentinel Dashboard HTTP 200。

### D.8 异常、工具问题与限制

- 20 QPS 点赞首轮 45 个 503、20 QPS 重跑 1 个 503、50 QPS 点赞 222 个 503 均保留在表中。根因是 2 vCPU VM 在 flush revision churn 和高并发下把 Redis 进程长时间调度出去，Lettuce 2 秒超时返回 503；不是 Lua 返回业务失败。
- 取消点赞 50 QPS 的前两次尝试（02:52:08、02:52:47）在 3 秒内结束且没有 JTL 样本。原因是直接传给 JMeter 的 CSV 令牌路径是相对路径，JMeter 按 JMX 所在目录解析后无法打开文件；改用绝对路径后 02:53:29 正常执行。两次 0 样本轮次不是压测结果，不纳入容量表。
- 20 QPS 点赞重跑没有 CPU CSV（统计进程缺少 `VM_PASSWORD` 环境变量）；文中明确使用相邻轮次的 CPU 数据作参考，没有补造该轮采样。
- 30 QPS 取消和 50 QPS 取消分别为 34.7 秒、25.6 秒，原因是专用已点赞用户池有限；两轮的成功数与计数下降一致，不能把其“目标 QPS 达成率”直接外推到 60 秒稳态。
- 整机只有 2 vCPU、约 3.7 GiB 内存、2.5 GiB swap，14 个容器同时运行；点赞 50 QPS 时 load 23.76、CPU 100%。绝对容量和阈值只适用于当前单实例共享 VM，不能外推到生产；换机器、换实例数必须重测。
- 本阶段只清理/使用专用基准目标与用户，没有 TRUNCATE、没有无 WHERE 的 DELETE、没有删除数据卷；未改业务代码、未改旧阈值。回退时将 Nacos 与本地 community flow 恢复为 `[]` 即关闭两条规则。原始 JTL、summary 和 CPU CSV 保存在被 Git 忽略的 `scripts/sentinel-benchmark/results/`，不提交。

## Stage E：OSS 预签名 URL 与最终治理验收

2026-11-02 03:20–04:55 实测。入口为 `POST /video/create/upload-url` 和 `GET /lesson/get/play-url/{lessonId}`，经 Nginx、Gateway 到单实例 training-service（容器内 8082）。本阶段只测试 OSS 预签名 URL 生成，不上传视频字节，也不下载视频；每个请求都会创建并关闭 OSSClient 后签发短期地址。测试令牌使用专用基准用户 1992001（`benchmark_comm_00001`），播放课时为 199080，复用了 Stage16 已绑定的真实 OSS 对象和视频元数据，没有伪造 objectKey，没有上传文件。所有原始 JTL、summary 与 CPU CSV 仍在被 Git 忽略的 `scripts/sentinel-benchmark/results/`。

### E.1 环境、工具与口径

- Windows 本机 JMeter 5.6.3 CLI；外部入口 `http://192.168.10.102/`；单 Gateway、单 training 实例；VMware CentOS 2 vCPU、约 3.7 GiB RAM、约 3 GiB swap，14 个 Docker Compose 容器同时运行。
- 每轮 60 秒、ramp 5 秒（另注明除外），ConstantThroughputTimer 控制目标；实际 QPS 按 JTL 首请求开始至末请求完成的时间窗计算；P90/P95/P99 为全量样本 nearest-rank；Error% 包含非 200 响应。
- 稳定性判据与 B/C/D 相同：实际吞吐 ≥ 目标 95%、无 5xx/超时/连接错误、P95 不明显失控、CPU 不持续饱和；同时观察 OSS SDK 客户端创建/关闭、线程等待和整机 CPU。
- 关闭 flow 的裸容量阶段先确认 training flow 中两条 OSS 规则仍为 `[]`；完成容量测试后写入 8 QPS，再做带 flow 的 0.75T、1.0T、1.5T、2.0T 复测。没有把裸容量和启用态结果混在同一张表。

### E.2 未治理裸容量：oss-upload-url

第一批用 50–800 目标、15–200 线程，线程数不足以打满目标，只作为诊断证据，不用于稳定点结论：

| 目标 QPS | 线程 | 样本 | 实际 QPS | P95 ms | 异常 |
| ---: | ---: | ---: | ---: | ---: | --- |
| 50 | 15 | 678 | 10.932 | 4,583 | 0 |
| 100 | 30 | 1,254 | 19.123 | 6,471 | 0 |
| 200 | 60 | 1,343 | 22.422 | 6,772 | 0 |
| 300 | 80 | 2,412 | 38.297 | 4,320 | 0 |
| 500 | 120 | 2,378 | 38.434 | 8,086 | 0 |
| 800 | 200 | 3,100 | 49.668 | 6,933 | 16 个 HTTP 504，Error 0.516% |

800 目标轮的 16 个 504 是客户端/线程与共享 VM 调度共同造成的实际失败，保留在此，不纳入稳定点。随后用足够线程复测：

| 目标 QPS | 线程 | 样本 | 实际 QPS | P95 ms | 200 / 异常 | 机器 CPU 均值/峰值 | load 峰值 |
| ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: |
| 5 | 20 | 315 | 5.313 | 154 | 315 / 0 | 60.7% / 82.3% | 16.02 |
| 10 | 30 | 622 | 10.472 | 133 | 622 / 0 | 66.8% / 100% | 15.86 |
| 20 | 60 | 1,184 | 19.426 | 4,647 | 1,184 / 0 | 87.2% / 100% | 21.22 |
| 30 | 90 | 1,860 | 31.370 | 1,181 | 1,860 / 0 | 87.0% / 98.5% | 20.85 |
| 50 | 150 | 2,932 | 48.808 | 4,134 | 2,932 / 0 | 97.0% / 100% | 20.91 |

### E.3 未治理裸容量：oss-play-url

| 目标 QPS | 线程 | 样本 | 实际 QPS | P95 ms | 200 / 异常 | 机器 CPU 均值/峰值 | load 峰值 |
| ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: |
| 5 | 20 | 315 | 5.318 | 275 | 315 / 0 | 62.2% / 96% | 13.94 |
| 10 | 30 | 621 | 10.498 | 145 | 621 / 0 | 67.2% / 100% | 6.85 |
| 20 | 60 | 1,241 | 20.926 | 1,623 | 1,241 / 0 | 77.9% / 99.6% | 13.73 |
| 30 | 90 | 1,849 | 31.171 | 496 | 1,849 / 0 | 87.5% / 100% | 14.45 |
| 50 | 150 | 2,813 | 46.777 | 4,998 | 2,813 / 0 | 97.2% / 99.9% | 19.92 |

两个 OSS 资源在 10 QPS 时实际吞吐分别为 10.472 和 10.498，P95 为 133/145ms，Error=0，是最后完全稳定点；20 QPS 起延迟明显恶化（P95 4,647/1,623ms），50 QPS 时整机 CPU 已到 97%，吞吐约 47–49 QPS，P95 4–5 秒，属于过载区。两资源的裸容量曲线接近，原因是瓶颈都在 training JVM 内每次请求创建/关闭 OSSClient 及整机 CPU，而不是上传与播放签名逻辑本身。

### E.4 最终阈值与 flow 配置

**oss-upload-url = 8 QPS/实例，oss-play-url = 8 QPS/实例**。两资源稳定点均为 10 QPS，按 80% 取 8，低于 20 QPS 的拐点，也为共享 VM 上的 GC、Nacos 心跳和其它业务保留余量；没有取 10（贴稳定点、无余量），也没有取 12（已进入延迟上升区）。阈值是每个 training JVM 的本地 FlowRule，不是集群总配额。

Nacos `campustrain-dev`、group `CAMPUS_TRAIN`、DataId `training-service-sentinel-flow-dev.json` 与本地文件均已写入以下四条规则；前两条旧规则保持原值不变：

```json
[
  {"resource":"training-detail","limitApp":"default","grade":1,"count":800,"strategy":0,"controlBehavior":0},
  {"resource":"training-enrollment-check","limitApp":"default","grade":1,"count":750,"strategy":0,"controlBehavior":0},
  {"resource":"oss-upload-url","limitApp":"default","grade":1,"count":8,"strategy":0,"controlBehavior":0},
  {"resource":"oss-play-url","limitApp":"default","grade":1,"count":8,"strategy":0,"controlBehavior":0}
]
```

发布后从 Nacos 读回，与本地文件逐字节一致；没有删除或修改 `training-detail` 800、`training-enrollment-check` 750。

### E.5 开启 Sentinel 后复测

最终采用带 CPU 采集的一批数据；所有轮次均 0 个 503、0 timeout、0 connection refused/reset：

| 资源 | 目标 QPS | 线程 | 样本 | 实际 QPS | 200 | 429 | 成功 QPS | P95 ms | CPU 均值/峰值 | load 峰值 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| upload | 6（0.75T） | 6 | 360 | 6.071 | 360 | 0 | 6.071 | 106 | 59.2% / 94.44% | 7.14 |
| upload | 8（1.0T） | 8 | 482 | 8.132 | 477 | 5 | 7.950 | 119 | 58.7% / 87.5% | 4.27 |
| upload | 12（1.5T） | 12 | 721 | 12.169 | 478 | 243 | 7.967 | 162 | 66.4% / 88.24% | 8.95 |
| upload | 16（2.0T） | 16 | 963 | 16.239 | 478 | 485 | 7.967 | 117 | 64.1% / 76.84% | 8.44 |
| play | 6（0.75T） | 6 | 362 | 6.093 | 361 | 1 | 6.017 | 150 | 58.0% / 100% | 6.41 |
| play | 8（1.0T） | 8 | 481 | 8.099 | 471 | 10 | 7.850 | 207 | 60.7% / 97.07% | 7.62 |
| play | 12（1.5T） | 12 | 726 | 12.216 | 477 | 249 | 7.950 | 206 | 61.6% / 78.57% | 9.12 |
| play | 16（2.0T） | 16 | 965 | 16.223 | 477 | 488 | 7.950 | 169 | 63.7% / 77.65% | 13.8 |

成功吞吐被稳定限制在约 7.8–8.0 QPS，8 QPS 目标轮 429 很少（upload 5、play 10）；12/16 QPS 的额外流量主要被 429 拒绝。所有 429 均来自 `FlowException`，没有把 503 或超时计成 429。

另有一批未采集 CPU 的首轮数据保留为对照：upload 在 6/8/12/16 目标的 429 分别为 0/15/250/487，play 为 2/27/174/485；play 12 QPS 首轮实际 9.137、成功 6.204、P95 2,497ms，明显受共享 VM 饱和影响。该批不作为最终 CPU 证据，但不能从记录中删除；最终数值以上表带 CPU 批次为准。

### E.6 oss-play-url 受控 degrade 实验

选择异常比例（grade=1）而不是 RT 规则：正常预签名请求的 RT 很低，共享 VM 抖动会使 RT 阈值不稳定；用无效 lessonId 可以精确制造方法异常，验证打开、半开和恢复。规则写入 `training-service-sentinel-degrade-dev.json`，同时保留原有两条 `training-detail` 规则：

```json
[
  {"resource":"training-detail","grade":0,"count":500,"timeWindow":5,"minRequestAmount":5,"slowRatioThreshold":0.5,"statIntervalMs":10000},
  {"resource":"training-detail","grade":1,"count":0.5,"timeWindow":5,"minRequestAmount":5,"statIntervalMs":10000},
  {"resource":"oss-play-url","grade":1,"count":0.5,"timeWindow":10,"minRequestAmount":5,"statIntervalMs":1000}
]
```

实验步骤与结果：

1. 基线：有效 `lessonId=199080` 单次请求 HTTP 200，耗时 62.4ms。
2. 0.225 秒内并发 8 个无效 `lessonId=199999999` 请求，全部 HTTP 200，但响应 body 为 `code=400`、`"课时不存在"`。全局异常处理器把业务异常包装成 HTTP 200；Sentinel 的方法异常统计仍记录到这些异常。
3. 随后 3 次有效请求均返回 HTTP 503，body `code=503`、`"视频服务暂时不可用"`，耗时 1,196.4/79.7/34.8ms；日志出现三行 `blockType=DegradeException`，证明熔断打开。
4. 10.646 秒后单次有效请求恢复 HTTP 200、75.1ms；之后连续 5 次有效请求均为 HTTP 200（46.9–79.9ms），证明 timeWindow=10 秒后进入半开并恢复。

该实验说明：`oss-play-url` 的降级保护能把持续方法异常快速转成 503，而不是让异常请求继续穿透；`oss-upload-url` 本阶段只配置 flow，没有为其伪造 degrade 阈值。degrade 规则已发布到 Nacos 并读回，与本地文件逐字节一致；实验注入只使用无效 lessonId，没有改业务代码。

### E.7 最终高负载保护验收

六条 flow 全部启用后，按用户要求至少对 auth-login、learning-progress-report、community-like 做最终高负载验收：

| 资源 | 目标/线程/时长 | 样本 | 实际 QPS | 200 | 429 | 503 | timeout/connection | 成功 QPS | 总 P95 ms | 成功 P95 ms | Max ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| auth-login | 30 / 30 / 60s | 1,343 | 20.532 | 179 | 1,164 | 0 | 0 / 0 | 2.737 | 4,001 | 6,848 | 7,115 |
| learning-progress-report | 32 / 32 / 60s | 1,230 | 20.023 | 407 | 823 | 0 | 0 / 0 | 6.626 | 4,458 | 7,560 | 13,768 |
| community-like | 32 / 32 / 60s | 1,692 | 28.475 | 464 | 1,228 | 0 | 0 / 0 | 7.824 | 1,861 | 2,878 | 3,921 |

三者的成功吞吐都明显低于目标并被阈值封顶，超额流量主要返回 429，0 个 503，0 超时/连接拒绝/重置；服务在验收后仍健康。auth 成功 QPS 只有约 2.7，是 BCrypt 与整机 CPU 的既有瓶颈；learning 成功约 6.6，community-like 成功约 7.8，均与前面 B/C/D 的阈值结论一致。

learning 最终高负载第一次运行使用了过期 JWT，1,887 个样本全部 401，该轮标记为无效，不纳入验收；随后通过真实 `/auth/login` 为 200 个专用用户重新签发令牌，在约 3 QPS 登录限制下完成后再跑，上表是重跑结果。该失败轮保留在 results 中，不用来证明任何容量结论。

### E.8 最终业务回归

在六条 flow 和 oss-play-url degrade 全部启用的状态下执行：

1. 使用专用用户 1992001 通过 `POST /auth/login` 登录，HTTP 200、code 200。
2. `GET /training/get/199001` HTTP 200，返回培训详情，enrolledCount=1,001。
3. `POST /enrollment/create` body `{"trainingId":199001}` 首次调用在 30 秒客户端读超时；服务端随后于 04:47:16 提交 enrollmentId=7618，training enrolled_count 变为 1,002。客户端超时是长时间空闲后 Feign/Nacos 订阅冷启动造成的，后续 `GET /enrollment/list/mine` HTTP 200 已确认有效报名；该失败没有被隐藏或改写为 200。
4. `POST /learning/start/199080`、`POST /learning/report-progress/199080` body `{"progressSeconds":30}`、`GET /learning/get-progress/199080` 均 HTTP 200，进度 30/100、completed=false；MySQL learning_record 与 Redis 进度随后对齐。
5. `GET /growth/get/total-ranking?limit=5` HTTP 200 并返回排名；`GET /growth/get/my-ranking` HTTP 200 但 body 为空，因为该基准用户没有积分，不是接口错误。
6. `GET /community/get/like-status` 返回 411；`POST /community/like` 200，count 411→412、changed=true；`POST /community/unlike` 200，count 412→411、changed=true；最终 like-status 为 411，没有负值。
7. `POST /video/create/upload-url` 首次传入带 `/` 的文件名，按业务校验返回 `code=400`；改用合法文件名 `stage19-regression.mp4` 后 HTTP 200 并生成 objectKey/uploadUrl。`GET /lesson/get/play-url/199080` HTTP 200 并生成 playUrl。两个签名 URL 的完整值和签名参数不写入文档。

`docker ps` 显示 14 个容器全部 healthy，所有容器 RestartCount=0；业务回归后 Nginx、Gateway、training、auth、learning、community、growth 均可正常响应。首次报名 30 秒超时是本次回归唯一的客户端失败，已在上面明确记录。

### E.9 最终数据一致性

等待 dirty 归零后执行全量核对，不只抽查最终目标：

- learning：Redis 中 3,173 个 `campustrain:learning:progress:*` 键全部能在 MySQL 找到对应记录，`progress_seconds` 与 `completed` 不一致数为 0，缺失数为 0；`campustrain:learning:dirty` 为 0；`completed=1 且 progress_seconds < duration_seconds*0.9` 的记录为 0。没有错误完成、进度回退或脏数据。
- community：11 个 `campustrain:community:like:count:*` 键全部满足 Redis count = Redis users = MySQL like_count = status=1 有效关系数，不一致数为 0；`campustrain:community:like:dirty` 为 0；question/answer 负 like_count 为 0；重复 status=1 关系为 0；0/1 之外的 status 为 0。
- 专用目标 QUESTION 199000001 最终 Redis count=411、users=411，MySQL like_count=411、有效关系=411、取消关系=1；回归用户的 like→unlike 已回到 411，没有残留有效关系或负数。
- `campus_learning.event_outbox` 仍为 6 行历史数据，基准用户完成事件为 0；`campus_enrollment` 中用户 1992001 有 1 条有效报名（enrollmentId=7618）。没有执行 TRUNCATE、无 WHERE 的 DELETE 或数据卷删除。

### E.10 Nacos、Dashboard 与健康检查

五份配置均在 namespace `campustrain-dev`、group `CAMPUS_TRAIN` 下从 Nacos 读回，与 Git 本地文件逐字节一致：auth flow、learning flow、community flow、training flow、training degrade。auth/learning 在最终检查时曾出现内容相同但尾部换行不一致，重新发布本地内容后读回 exactMatch=True；没有改变规则值。

Sentinel Dashboard 登录成功；当前 healthy training 实例 `172.19.0.9:8719` 的资源列表同时包含 `/video/create/upload-url`、`oss-upload-url`、`/lesson/get/play-url/{lessonId}`、`oss-play-url`、`/training/get/{trainingId}`、`training-detail`、`/internal/training/get-enrollment-check/{trainingId}`、`training-enrollment-check`。auth/learning/community 的六个新资源也都在各自 healthy 实例上显示。Dashboard 仍保留训练服务 5 条 machine 记录（仅 1 条 healthy）和 Gateway 3 条记录（仅 1 条 healthy），属于历史实例残留；不影响当前路由和资源展示，本次未删除容器或历史记录。

04:50:19–04:51:08 出现一次 Nacos 客户端 gRPC 重连窗口，多个服务日志出现 `Server check fail...9848`、`Client not connected, current status:UNHEALTHY`；04:51:08 后至验收结束没有新增 ERROR，Nacos readiness 返回 OK，8 个服务实例在 Nacos 中均为 healthy。该窗口没有产生新的业务请求失败记录；此前 04:45 的报名超时是 Feign 冷启动，不是这次重连。

### E.11 限制

- 2 vCPU / 约 3.7 GiB 共享 VM、14 个容器同时运行；OSS 容量和 P95 受整机 CPU、线程调度和 OSSClient 创建/关闭影响，绝对值不能外推到生产；换机器、换实例数必须重测。
- 只测试预签名 URL 生成，没有真正上传/下载视频，因此没有覆盖 OSS 服务端网络延迟、带宽和对象权限；签名 URL 未写入文档。
- upload 第一批 50–800 目标线程不足，800 轮出现 16 个 504；该批保留为诊断证据，不用于稳定点。play 首批未采集 CPU，12 QPS 首轮出现实际 9.137、成功 6.204、P95 2,497ms 的 VM 饱和值；最终采用带 CPU 的重跑批次。
- degrade 实验用无效 lessonId 注入方法异常，业务全局异常处理器返回 HTTP 200 + body code 400；这是本文明确记录的口径，不代表 Sentinel 把异常 HTTP 状态当作成功。
- 单实例结果不代表多实例总容量；阈值是每实例本地规则。若需回退，删除对应 flow/degrade 条目或恢复为空数组即可；本次没有修改旧阈值和业务代码。

## Topology A 最终资源表（历史）

| 资源 | 服务 | 保护类型 | 裸容量稳定点 | 拐点 | 峰值 | 最终 flow QPS | degrade 规则 | 证据 |
| --- | --- | --- | --- | --- | --- | ---: | --- | --- |
| training-detail | training-service | flow + RT/异常比例 degrade | 800–1,000 QPS（800 P95 22ms；1,000 P95 23ms） | 1,100 QPS（P95 183ms） | 1,165.7 QPS @1,200 目标 | 800 | RT>500ms、比例>0.5、minRequestAmount=5、statIntervalMs=10000、timeWindow=5s | [Stage10 基准](../stage10/benchmark-results.md) |
| training-enrollment-check | training-service | flow | 1,000 QPS（958.7，P95 154ms） | 1,200 QPS（1,091.2，P95 264ms） | 1,150.7 QPS @1,400 目标 | 750 | — | [Stage10 基准](../stage10/benchmark-results.md) |
| enrollment-create | enrollment-service | flow | 200 QPS（198.4，P95 483ms） | 250 QPS（P95 413–629ms） | 302 QPS @500 目标 | 160 | — | [Stage10 基准](../stage10/benchmark-results.md) |
| Gateway enrollment route | campus-gateway | flow | 200 QPS（201.2–201.8，P95 76–402ms） | 300 QPS（P95 841ms） | 281.4 QPS @500 目标 | 150 | — | [Stage10 基准](../stage10/benchmark-results.md) |
| auth-login | auth-service | flow | 4 QPS（4.041，Error=0） | 4–5 QPS（5 目标仅 87.2%） | 4.36 QPS | 3 | — | 本文 B.2–B.4 |
| learning-progress-report | learning-service | flow | 10 QPS（10.017，P95 1,985ms） | 10–15 QPS（15 重跑 92.7%，P95 7,509ms） | 13.91 QPS | 8 | — | 本文 C.2–C.5 |
| community-like | community-service | flow | 10 QPS（10.236，P95 993ms） | 20–30 QPS（20 重跑 19.331，P95 1,263ms；30 P95 2,629ms） | 29.07 QPS | 8 | — | 本文 D.2、D.3、D.5 |
| community-unlike | community-service | flow | 30 QPS（29.464，P95 1,348ms） | 50 QPS（72.4%，P95 3,639ms） | 36.22 QPS | 8 | — | 本文 D.4、D.6 |
| oss-upload-url | training-service | flow | 10 QPS（10.472，P95 133ms） | 20 QPS（19.426，P95 4,647ms） | 48.808 QPS @50 目标 | 8 | — | 本文 E.2、E.5 |
| oss-play-url | training-service | flow + 异常比例 degrade | 10 QPS（10.498，P95 145ms） | 20 QPS（20.926，P95 1,623ms） | 46.777 QPS @50 目标 | 8 | grade=1、比例>0.5、minRequestAmount=5、statIntervalMs=1000、timeWindow=10s | 本文 E.3、E.5、E.6 |

## Topology B（4 vCPU / 7.6 GiB）正式基准

> 以下为最终正式基准。Topology B 的虚拟机为 4 vCPU、MemTotal 7,803 MiB、Swap 3.0 GiB（压测期间使用 0）、14 个 Docker Compose 容器均 healthy；正式空载基线 CPU idle 约 87.6%、1min load 约 1.88。所有入口仍为 `http://192.168.10.102/`，链路 Nginx → Gateway → 单业务实例，不使用 Windows Docker Desktop。

### B.1 环境与口径（Topology B）

2026-11-02 11:25–11:39 实测。入口固定为 `POST http://192.168.10.102/auth/login`，链路 Nginx → Gateway → auth-service，单 auth 实例。请求使用专用基准账号 `benchmark_sentinel_student`（userId=1990001）和真实 `AuthenticationManager` + `BCryptPasswordEncoder.matches()`；数据库哈希成本因子为 `$2b$10$`，不是 mock，也不是错误密码。密码只通过进程环境变量注入 JMeter，不写入仓库、文档、脚本或日志。

压测前已将 Nacos `campustrain-dev / CAMPUS_TRAIN / auth-service-sentinel-flow-dev.json` 临时发布为 `[]` 并读回确认，裸容量阶段没有流控。JMeter 5.6.3 CLI，每轮 60 秒、ramp 5 秒，warm-up 3 QPS / 20 秒不计入结论；实际 QPS 按 JTL 首请求开始至末请求完成的时间窗计算，P90/P95/P99 为全量样本 nearest-rank。稳定性判据：实际吞吐 ≥ 目标 95%、无 5xx、无 timeout/connection refused/reset、业务数据正确、P95 不明显跳升、整机 CPU 不持续打满。

### B.2 未治理裸容量（auth-login）

| 目标 QPS | 线程 | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 / 429 / 503 / timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 5 | 10 | 302 | 5.126 | 102.5% | 286 | 386 | 446 | 509 | 577 | 0 | 302 / 0 / 0 / 0 |
| 10 | 20 | 585 | 10.120 | 101.2% | 554 | 966 | 1,318 | 1,811 | 2,062 | 0 | 585 / 0 / 0 / 0 |
| 15 | 30 | 875 | 14.862 | 99.1% | 1,143 | 1,839 | 2,110 | 2,552 | 2,940 | 0 | 875 / 0 / 0 / 0 |
| 20 | 40 | 780 | 12.884 | 64.4% | 2,864 | 4,172 | 5,751 | 9,451 | 12,131 | 0 | 780 / 0 / 0 / 0 |
| 30 | 60 | 862 | 14.693 | 49.0% | 3,976 | 5,406 | 5,691 | 6,117 | 6,812 | 0 | 862 / 0 / 0 / 0 |
| 40 | 80 | 903 | 15.150 | 37.9% | 5,126 | 7,126 | 7,489 | 8,099 | 9,757 | 0 | 903 / 0 / 0 / 0 |
| 50 | 100 | 905 | 15.037 | 30.1% | 6,449 | 8,754 | 9,395 | 10,452 | 14,041 | 0 | 905 / 0 / 0 / 0 |

warm-up 3 QPS / 6 线程：59 样本、实际 3.235 QPS、P95 889ms、Error=0，仅用于预热，不纳入容量结论。

CPU 采样（每 10 秒，去掉首末各 1 个采样点；auth 容器百分比是单核百分比，可超过 100%；机器 CPU 为整机百分比）：

| 目标 QPS | 机器 CPU 均值/峰值 | auth 容器 CPU 均值/峰值 | load1 均值/峰值 | 观察 |
| ---: | ---: | ---: | ---: | --- |
| 5 | 46.3% / 55.4% | 120.1% / 185.8% | 2.3 / 2.4 | 轻载，P95 446ms |
| 10 | 69.7% / 81.6% | 214.8% / 290.0% | 6.4 / 7.5 | 最后稳定点，仍有余量 |
| 15 | 92.9% / 99.7% | 339.7% / 359.0% | 10.0 / 13.7 | 吞吐达标，但 CPU 已持续接近打满 |
| 20 | 89.6% / 99.8% | 380.2% / 424.1% | 20.0 / 28.3 | 吞吐回落，排队明显 |
| 30 | 86.9% / 99.9% | 300.6% / 345.1% | 28.0 / 40.6 | 饱和平台，P95 5.7s |
| 40 | 86.7% / 99.9% | 347.3% / 394.4% | 38.3 / 54.4 | 峰值吞吐 15.150，P95 7.5s |
| 50 | 87.3% / 99.9% | 364.9% / 385.1% | 50.4 / 70.8 | 过载，P95 9.4s |

**结论**：最后稳定点是 10 QPS（实际 10.120，为目标 101.2%，Error=0，P95 1,318ms，机器 CPU 69.7%/81.6%，load 6.4）；第一个明显拐点在 10 与 15 QPS 之间，15 QPS 虽然实际 14.862（99.1%）且 0 错误，但机器 CPU 已到 92.9%/99.7%、load 10.0，按“CPU 不持续打满”的判据不算稳定点；已观测吞吐峰值为 15.150 QPS（40 QPS 目标）。20/30/40/50 QPS 目标下吞吐停留在 12.884–15.150，P95 5.7–9.4 秒、load 20–71，属于饱和平台，不是可用的稳定容量。瓶颈是 BCrypt 的 CPU 成本与 4 vCPU 共享 VM，而不是业务异常或流控；全程 0 个 5xx、0 timeout、0 connection refused/reset。

### B.3 最终阈值（auth-login）

**auth-login = 8 QPS/实例**。理由：Topology B 的最后稳定点是 10 QPS，阈值取 80%（10 → 8），低于 10–15 之间的拐点，也明显低于已观测峰值 15.150 QPS；8 QPS 为同一 VM 上的 GC、Nacos 心跳、Gateway 和其它业务实例保留约 20% 的吞吐余量。没有沿用 Topology A 的 3 QPS，也没有取 10（贴着稳定点、无余量）或 12（已进入 15 QPS 的 CPU 饱和区）。阈值是每个 auth JVM 的本地 FlowRule，不是集群总配额。

已写入 Nacos `campustrain-dev`、group `CAMPUS_TRAIN`、DataId `auth-service-sentinel-flow-dev.json`，并读回校验；本地 `config/nacos/auth-service-sentinel-flow-dev.json` 同步为相同内容：

```json
[
  {"resource":"auth-login","limitApp":"default","grade":1,"count":8,"strategy":0,"controlBehavior":0}
]
```

### B.4 开启 Sentinel 后复测

2026-11-02 11:48–11:54，Nacos 与本地文件均为 `count=8`，读回一致。仍使用真实账号与 BCrypt 校验，负载为 0.75T / 1.0T / 1.5T / 2.0T（6 / 8 / 12 / 16 QPS），每轮 60 秒、ramp 5 秒：

| 负载 | 线程 | 样本 | 实际 QPS | 200 | 429 | 429 占比 | 成功 QPS | 总 P95 ms | 成功 P95 ms | 总 P99 ms | 503 | timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 6（0.75T） | 8 | 345 | 5.905 | 345 | 0 | 0% | 5.905 | 622 | 622 | 2,167 | 0 | 0 |
| 8（1.0T） | 12 | 471 | 8.143 | 353 | 118 | 25.1% | 6.116 | 423 | 585 | 913 | 0 | 0 |
| 12（1.5T） | 18 | 715 | 12.253 | 430 | 285 | 39.9% | 7.369 | 326 | 388 | 484 | 0 | 0 |
| 16（2.0T） | 24 | 953 | 16.163 | 413 | 540 | 56.7% | 7.005 | 563 | 828 | 1,153 | 0 | 0 |

阈值上方与阈值附近的超量请求全部以 HTTP 429 返回，未出现 503、5xx、timeout、connection refused/reset；成功吞吐稳定在约 6.1–7.4 QPS，与 8 QPS 阈值的秒级窗口利用率一致。8 QPS 目标下已有 25.1% 的 429：Sentinel QPS 规则按 1 秒窗口计数，JMeter 的 ConstantThroughputTimer 在响应变慢时会产生秒内突发，属于阈值附近的正常窗口效应，不是服务错误。压测结束后的普通登录为 HTTP 200、业务 code 200。

本轮 enabled 复测未采集容器 CPU：`collect_stats.py` 需要 `VM_PASSWORD` 环境变量建立 SSH 采样，该变量在后台任务环境中未提供，输出为 `Set VM_PASSWORD for SSH access`。不补填推测值；成功吞吐最高 7.37 QPS，低于裸容量 10 QPS（机器 CPU 均值/峰值 69.7%/81.6%），因此 CPU 上界可参考 B.2 的 10 QPS 行，且实际应更低。

### B.5 健康、回退与限制

- 健康检查：`http://192.168.10.102/nginx-health` 返回 200，Nacos readiness 返回 200，Sentinel Dashboard 根路径返回 200；压测后真实登录返回 200；全程无 5xx、timeout、connection refused/reset。
- 本轮没有重建、重启或删除任何容器，没有修改 auth 业务代码、数据库结构或其它 Sentinel 资源。
- 回退方式：将 Nacos 与本地 `config/nacos/auth-service-sentinel-flow-dev.json` 同时发布为 `[]` 即关闭 auth-login 流控；恢复时重新发布上面的 8 QPS 规则。阈值是每个 auth JVM 的本地规则，不是集群总配额。
- 限制：结论来自 4 vCPU / 7,803 MiB、单 auth 实例、共享 14 容器 VM；机器规格、BCrypt 成本因子、实例数变化后必须重测。Topology A 的 3 QPS 结论只适用于 2 vCPU / 3.7 GiB 资源受限环境，不再作为当前阈值依据。

### C.1（Topology B）环境、数据与口径

2026-11-02 12:02–12:22 实测裸容量。入口固定为 `POST http://192.168.10.102/learning/report-progress/{lessonId}`，链路 Nginx → Gateway → learning-service，单 learning 实例。使用 1,000 个专用基准用户 1990100–1991099 的预签发 JWT；每请求真实执行 Feign 调 training-service 取课时、Feign 调 enrollment-service 校验有效报名、Redis Lua 原子更新进度并写 dirty set，再由 `@Scheduled(fixedDelay=5000)` 每批 100 个成员刷入 MySQL `learning_record`，不是 mock，也没有绕过真实链路。JWT 有效期 2 小时，压测期间不重新签发。

裸容量前将 Nacos `campustrain-dev / CAMPUS_TRAIN / learning-service-sentinel-flow-dev.json` 临时发布为 `[]` 并读回确认。JMeter 5.6.3 CLI，每轮 60 秒、ramp 5 秒；warm-up 为 10 QPS / 15 秒，不计入结论。实际 QPS 按 JTL 首请求开始至末请求完成的时间窗计算，P90/P95/P99 为全量样本 nearest-rank。每轮使用一个新的视频课时（199025–199029、199031–199033，时长 100 秒、完成阈值 90 秒），JMeter 按用户维护单调递增的 `progressSeconds`，避免进度回退影响结论。稳定性判据：实际吞吐 ≥ 目标 95%、无 5xx、无 timeout/connection refused/reset、业务数据正确、P95 不明显跳升、整机 CPU 不持续打满。

### C.2（Topology B）未治理裸容量（learning-progress-report）

| 目标 QPS | 线程 | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 / 429 / 503 / timeout | dirty 清零 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| 10 | 30 | 618 | 10.448 | 104.5% | 90.9 | 159 | 257 | 582 | 1,381 | 0 | 618 / 0 / 0 / 0 | 4.0s |
| 20 | 50 | 1,232 | 20.807 | 104.0% | 48.6 | 81 | 107 | 200 | 402 | 0 | 1,232 / 0 / 0 / 0 | 14.0s |
| 30 | 80 | 1,849 | 31.264 | 104.2% | 44.6 | 78 | 113 | 191 | 369 | 0 | 1,849 / 0 / 0 / 0 | 36.0s |
| 50 | 120 | 3,058 | 51.912 | 103.8% | 52.2 | 88 | 141 | 452 | 666 | 0 | 3,058 / 0 / 0 / 0 | 46.1s |
| 80 | 180 | 4,876 | 82.727 | 103.4% | 36.9 | 63 | 110 | 301 | 565 | 0 | 4,876 / 0 / 0 / 0 | 52.1s |
| 100 | 220 | 6,085 | 103.474 | 103.5% | 29.9 | 48 | 64 | 185 | 327 | 0 | 6,085 / 0 / 0 / 0 | 54.1s |
| 150 | 320 | 9,066 | 150.718 | 100.5% | 152.7 | 105 | 1,479 | 2,284 | 2,970 | 0 | 9,066 / 0 / 0 / 0 | 60.1s |
| 200 | 420 | 11,424 | 193.804 | 96.9% | 418.5 | 1,522 | 2,629 | 3,828 | 4,871 | 0 | 11,424 / 0 / 0 / 0 | 56.1s |

warm-up 10 QPS / 15 秒：30 样本、实际 1.947 QPS、Avg 15,170ms、P95 15,396ms、全部 HTTP 200。该轮是 JIT、连接池和 Feign 客户端冷启动导致的异常轮次，保留记录但不用于容量结论；紧随其后的正式 10 QPS 轮 Avg 90.9ms、P95 257ms。

**结论**：最后稳定点是 100 QPS（实际 103.474，为目标 103.5%，P95 64ms，P99 185ms，Error=0）。第一个明显拐点在 100 与 150 QPS 之间：150 QPS 虽然实际 150.718（100.5%）且 0 错误，但 P50 仍为 27ms、P90 105ms、P95 跳到 1,479ms、P99 2,284ms，平均延迟从 29.9ms 升到 152.7ms，属于典型双峰排队；200 QPS 实际 193.804（96.9%），P90 1,522ms、P95 2,629ms、P99 3,828ms，进入饱和平台。已观测吞吐峰值为 193.804 QPS（200 QPS 目标）。10–100 QPS 区间吞吐随目标线性增长、P95 64–257ms、无任何 4xx/5xx/timeout/connection 错误；超过 100 QPS 后瓶颈表现为排队延迟而不是请求失败。按“P95 不明显跳升”的稳定判据，150 与 200 都不算稳定点。

### C.3（Topology B）Redis、落库与数据一致性

- 裸容量全部课时（199025–199029、199031–199033）刷盘后校验：Redis 进度 key 7,618 个，MySQL `learning_record` 7,618 行，缺失 0、进度/完成状态不一致 0；Redis completed=0，最大进度 13 秒，远低于 100 秒课时的 90 秒完成阈值；dirty set 为 0。
- 容量轮次期间 dirty set 峰值为 939（150 QPS 轮），每轮在 4.0–60.1 秒内清零；`flush-batch-size=100`、`flush-interval-ms=5000`，刷盘任务跟得上写入，MySQL 行数与 Redis key 数最终完全一致。
- 进度单调性：JMeter 按用户递增 `progressSeconds`，所有轮次未出现 400“进度超过视频时长”，MySQL 最大进度 13 秒；Lua 的防回退逻辑没有被绕过。
- 完成状态专项探针：用户 1990100、课时 199038，顺序上报 1–95 秒。89 秒时 `completed=false`，90 秒时变为 `completed=true`；dirty 清零后 Redis `progressSeconds=95, completed=1` 与 MySQL `progress_seconds=95, completed=1, status=2` 一致；`event_outbox` 从 6 行增至 7 行，新增 `LEARNING_COMPLETED` 事件。容量轮次没有产生任何 `LEARNING_COMPLETED` 事件，不存在误完成。

### C.4（Topology B）最终阈值（learning-progress-report）

**learning-progress-report = 80 QPS/实例**。理由：Topology B 的最后稳定点是 100 QPS，阈值取其 80%（100 → 80）；80 QPS 本轮实测 P95 110ms、Error=0，距离 100–150 之间的拐点有约 47% 余量，也明显低于 193.8 QPS 的观测峰值。没有沿用 Topology A 的 8 QPS；没有取 100（贴着稳定点、无余量）；也没有取 120（已进入 150 QPS 的排队区）。阈值是每个 learning JVM 的本地 FlowRule，不是集群总配额。

已写入 Nacos `campustrain-dev`、group `CAMPUS_TRAIN`、DataId `learning-service-sentinel-flow-dev.json`，并读回校验；本地 `config/nacos/learning-service-sentinel-flow-dev.json` 同步为相同内容：

```json
[
  {"resource":"learning-progress-report","limitApp":"default","grade":1,"count":80,"strategy":0,"controlBehavior":0}
]
```

### C.5（Topology B）开启 Sentinel 后复测

2026-11-02 12:25–12:35，Nacos 与本地文件均为 `count=80`，读回一致。负载为 0.75T / 1.0T / 1.5T / 2.0T（60 / 80 / 120 / 160 QPS），每轮 60 秒、ramp 5 秒：

| 负载 | 线程 | 样本 | 实际 QPS | 200 | 429 | 429 占比 | 成功 QPS | 总 P95 ms | 成功 P95 ms | 总 P99 ms | 503 | timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 60（0.75T） | 120 | 3,603 | 60.872 | 3,569 | 34 | 0.9% | 60.297 | 611 | 467 | 1,332 | 0 | 0 |
| 80（1.0T） | 160 | 4,895 | 82.580 | 4,367 | 528 | 10.8% | 73.672 | 53 | 54 | 93 | 0 | 0 |
| 120（1.5T） | 240 | 7,346 | 123.891 | 4,772 | 2,574 | 35.0% | 80.593 | 54 | 63 | 99 | 0 | 0 |
| 160（2.0T） | 320 | 9,702 | 164.835 | 4,725 | 4,977 | 51.3% | 80.277 | 102 | 122 | 238 | 0 | 0 |

阈值附近的超量请求全部以 HTTP 429 返回，没有 503、5xx、timeout、connection refused/reset。成功吞吐在 1.0T–2.0T 下稳定在 73.7–80.6 QPS，与 80 QPS 阈值一致；2.0T 下 429 占比 51.3%，说明流控有效拦截了超量请求，且成功请求的 P95 仍只有 122ms。0.75T 轮出现 0.9% 的 429，是 Sentinel 1 秒窗口与 JMeter ConstantThroughputTimer 秒内突发叠加的正常窗口效应，不代表阈值低于 60 QPS。该轮总 P95 611ms、成功 P95 467ms，明显高于后续 80/120/160 轮的 53–63ms，是服务在约 30 秒空闲后的 JIT、连接池和 Feign 客户端冷启动造成的异常轮次，保留记录但不用于阈值判断。

### C.6（Topology B）数据一致性、健康与回退

- enabled 复测四个课时（199034–199037）刷盘后：Redis 进度 key 4,000 个，MySQL `learning_record` 4,000 行，缺失 0、不一致 0；completed=0，最大进度 10 秒；dirty set 每轮在 50.1–54.1 秒内清零，最终为 0；容量轮次 `event_outbox` 中没有新增 `LEARNING_COMPLETED` 事件。
- 压测后校验：`http://192.168.10.102/nginx-health` 返回 200，Nacos readiness 返回 200，Sentinel Dashboard 返回 200；使用基准 token 对课时 199039 上报进度返回 HTTP 200、业务 code 200。
- 本轮没有重建、重启或删除任何容器，没有修改 learning 业务代码、数据库结构或其它 Sentinel 资源。
- 回退方式：将 Nacos 与本地 `config/nacos/learning-service-sentinel-flow-dev.json` 同时发布为 `[]` 即关闭 learning-progress-report 流控；恢复时重新发布上面的 80 QPS 规则。阈值是每个 learning JVM 的本地规则，不是集群总配额。
- 限制：`collect_stats.py` 依赖 `VM_PASSWORD` 建立 SSH 采样，本轮后台环境未提供该变量，因此没有整机 CPU、容器 CPU、load average 和 Swap 采样，输出为 `Set VM_PASSWORD for SSH access`；本文不补填推测值，容量结论基于实际吞吐、P95/P99、错误码、dirty set 与 MySQL 一致性。结论来自 4 vCPU / 7,803 MiB、单 learning 实例、共享 14 容器 VM 和 1,000 用户令牌池；机器规格、实例数、用户规模或 Flush 参数变化后必须重测。Topology A 的 8 QPS 结论只适用于 2 vCPU / 3.7 GiB 资源受限环境，不再作为当前阈值依据。

### D.1（Topology B）环境、目标与口径

2026-11-02 12:42–13:32 实测。入口固定为 `POST http://192.168.10.102/community/like` 与 `POST http://192.168.10.102/community/unlike`，链路 Nginx → Gateway → community-service，单 community 实例。使用 11,000 个专用基准用户 1992001–2003000，JWT 通过真实 `/auth/login` 获取；每请求真实执行 MySQL 问题存在性查询、Redis Lua 原子点赞/取消、dirty set 标记、每 5 秒按目标刷入 `like_relation` 并回写 `question.like_count`，不是 mock。

点赞裸容量使用“首次点赞”口径：每轮按 MySQL `like_relation` 过滤该目标从未点赞过的用户，JMeter 每请求取一个不同 token，避免重复点赞主导结果。10/20/30 QPS 使用问题 199000002，50 使用 199000005，80 使用 199000004，100 使用 199000003，150 使用 199000001；200 QPS 探针使用本轮新建的纯净问题 199000006（初始 like_count=0），11,000 个用户全部首次可点赞，因此 11,201 个样本中有 11,000 个首次点赞、约 1.8% 尾部重复点赞。取消点赞使用“真实已点赞用户”口径：10–150 QPS 从每个目标当前 `status=1` 的 `like_relation` 中选取基准用户，不预置 changed=false 快路径；200 QPS 探针在纯净问题 199000011 上以 flow 关闭状态通过真实 `/community/like` 预置 10,800 个活跃用户后再取消，探针尾部 13 个重复 token 的 no-op 单独记录，不构成快路径主导。

裸容量前将 Nacos `campustrain-dev / CAMPUS_TRAIN / community-service-sentinel-flow-dev.json` 临时发布为 `[]` 并读回确认。JMeter 5.6.3 CLI，点赞与取消点赞 10–150 QPS 每轮 60 秒，取消点赞 200 QPS 探针为 50 秒，ramp 5 秒；点赞 warm-up 为 5 QPS / 15 秒，不计入结论，取消点赞没有 warm-up，10 QPS 即首轮。实际 QPS、P90/P95/P99 均按 JTL 全量样本计算。每轮结束后等待 `campustrain:community:like:dirty` 清零，再比对 Redis users/count、MySQL `question.like_count`、`like_relation` 活跃关系数和重复活跃用户数；一致性要求 `Redis users = Redis count = MySQL like_count = MySQL 活跃关系数`，且重复活跃用户为 0。

### D.2（Topology B）点赞裸容量（Sentinel flow 关闭）

| 目标 QPS | 线程 | 目标问题 | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 / 429 / 503 / timeout | 点赞增量 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| 10 | 30 | 199000002 | 622 | 10.502 | 105.0% | 24.9 | 35 | 43 | 71 | 117 | 0 | 622 / 0 / 0 / 0 | 622 |
| 20 | 55 | 199000002 | 1,238 | 20.933 | 104.7% | 52.1 | 27 | 44 | 1,391 | 1,828 | 0 | 1,238 / 0 / 0 / 0 | 1,238 |
| 30 | 80 | 199000002 | 1,856 | 31.345 | 104.5% | 18.1 | 26 | 32 | 55 | 74 | 0 | 1,856 / 0 / 0 / 0 | 1,856 |
| 50 | 120 | 199000005 | 3,078 | 52.041 | 104.1% | 19.0 | 31 | 37 | 60 | 125 | 0 | 3,078 / 0 / 0 / 0 | 3,078 |
| 80 | 180 | 199000004 | 4,912 | 83.030 | 103.8% | 18.9 | 34 | 42 | 68 | 96 | 0 | 4,912 / 0 / 0 / 0 | 4,912 |
| 100 | 220 | 199000003 | 6,126 | 103.729 | 103.7% | 18.2 | 33 | 41 | 64 | 97 | 0 | 6,126 / 0 / 0 / 0 | 6,125 |
| 150 | 320 | 199000001 | 9,168 | 155.374 | 103.6% | 19.0 | 32 | 41 | 76 | 180 | 0 | 9,168 / 0 / 0 / 0 | 9,167 |
| 200（探针） | 420 | 199000006 | 11,201 | 189.002 | 94.5% | 448.9 | 1,861 | 3,106 | 4,134 | 5,570 | 0.312 | 11,166 / 0 / 35 / 0 | 11,000（唯一用户） |

warm-up 5 QPS / 15 秒（问题 199000005）：75 样本、实际 5.370 QPS、Avg 592ms、P95 3,282ms、全部 HTTP 200。该目标是首次初始化，Redisson 锁、MySQL 活跃用户集合复制和连接池冷启动叠加，保留为冷启动异常轮次，不用于容量结论；正式 50 QPS 轮 P95 37ms。

**结论**：10–150 QPS 区间点赞吞吐随目标线性增长，实际 10.502–155.374 QPS，P95 32–44ms，P99 55–76ms，Error=0；50 QPS 以后延迟基本不随负载变化，说明瓶颈已从服务线程转到 Redis/MySQL 共享资源但尚未饱和。最后稳定点是 150 QPS（实际 155.374，P95 41ms，P99 76ms，0 错误）。第一个明显拐点在 150 与 200 QPS 之间：200 QPS 探针实际只有 189.002（94.5%，低于 95% 判据），P90 1,861ms、P95 3,106ms、P99 4,134ms，并出现 35 个 HTTP 503（`点赞服务暂时不可用`，Redis/Redisson 侧异常路径），这是本轮唯一出现 5xx 的点赞轮次，明确判为不稳定。稳定吞吐峰值为 155.374 QPS；200 目标下的 189.002 QPS 是饱和吞吐，不是可用容量。

20 QPS 轮的 P99 1,391ms 是单次尾部尖峰（P90 27ms、P95 44ms），与 dirty flush/初始化尾部重合；50 QPS 及以上的 P99 均 ≤76ms，因此不把该尖峰判为拐点。100 与 150 QPS 各有 1 个重复点赞（`changed=false`），占样本 0.016% 和 0.011%，属于 JMeter 尾部取 token 的重复，不影响首次点赞口径。

### D.3（Topology B）最终阈值（community-like）

**community-like = 120 QPS/实例**。理由：Topology B 的最后稳定点是 150 QPS（实际 155.374，P95 41ms，0 错误），阈值取其 80%（150 → 120）；120 QPS 位于 10–150 的稳定平台内，距离 150–200 之间的拐点有 20% 目标余量，也明显低于 200 QPS 探针的饱和吞吐 189.002 QPS。没有沿用 Topology A 的 8 QPS；没有取 150（贴着稳定点、无余量）；也没有取 160 或更高（已进入 150–200 的饱和区）。阈值是每个 community JVM 的本地 FlowRule，不是集群总配额。

D.3 只确定 `community-like`；`community-unlike` 的独立裸容量与阈值见 D.4/D.5。两项最终一起写入 Nacos `campustrain-dev`、group `CAMPUS_TRAIN`、DataId `community-service-sentinel-flow-dev.json` 与本地文件，并读回校验。`community-like` 规则为：

```json
{"resource":"community-like","limitApp":"default","grade":1,"count":120,"strategy":0,"controlBehavior":0}
```

### D.4（Topology B）取消点赞裸容量（Sentinel flow 关闭）

2026-11-02 13:17–13:32 实测。取消点赞全部使用真实已点赞用户：10–150 QPS 从各目标当前 `status=1` 的 `like_relation` 中选取基准用户；200 QPS 探针在纯净问题 199000011 上以 flow 关闭状态通过真实 `/community/like` 预置 10,800 个活跃用户，再执行 50 秒取消负载。每轮校验取消增量：10–150 QPS 的 `after - before` 与 HTTP 200 数完全相等；200 QPS 探针 10,215 个样本中 10,202 个真实取消、13 个为重复 token 触发的 no-op（0.13%），不改变结论。

| 目标 QPS | 线程 | 目标问题 | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 / 429 / 503 / timeout | 取消增量 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| 10 | 30 | 199000002 | 617 | 10.451 | 104.5% | 28.0 | 45 | 99 | 289 | 730 | 0 | 617 / 0 / 0 / 0 | -600 |
| 20 | 55 | 199000002 | 1,237 | 20.932 | 104.7% | 17.7 | 29 | 38 | 68 | 88 | 0 | 1,237 / 0 / 0 / 0 | -1,200 |
| 30 | 80 | 199000002 | 1,853 | 31.355 | 104.5% | 17.8 | 28 | 36 | 61 | 76 | 0 | 1,853 / 0 / 0 / 0 | -1,800 |
| 50 | 120 | 199000005 | 3,072 | 52.028 | 104.1% | 18.0 | 32 | 46 | 83 | 133 | 0 | 3,072 / 0 / 0 / 0 | -3,000 |
| 80 | 180 | 199000004 | 4,905 | 82.926 | 103.7% | 20.5 | 28 | 50 | 241 | 415 | 0 | 4,905 / 0 / 0 / 0 | -4,800 |
| 100 | 220 | 199000003 | 6,130 | 103.677 | 103.7% | 16.4 | 26 | 32 | 52 | 119 | 0 | 6,130 / 0 / 0 / 0 | -6,000 |
| 150 | 320 | 199000001 | 9,165 | 154.966 | 103.3% | 18.9 | 28 | 41 | 156 | 342 | 0 | 9,165 / 0 / 0 / 0 | -9,000 |
| 200（探针） | 420 | 199000011 | 10,215 | 208.563 | 104.3% | 19.1 | 29 | 43 | 148 | 295 | 0 | 10,215 / 0 / 0 / 0 | -10,202 |

**结论**：10–150 QPS 区间取消点赞吞吐随目标线性增长，实际 10.451–154.966 QPS，P95 32–99ms，P99 52–289ms，Error=0，取消增量与成功请求数逐轮完全相等；50 QPS 以后 P95 稳定在 32–50ms。200 QPS 探针仍完全稳定：实际 208.563 QPS（104.3%），P95 43ms、P99 148ms、Max 295ms，0 个 4xx/5xx、0 timeout/connection error，说明取消路径在 200 QPS 目标下尚未饱和。10 QPS 是取消点赞首轮且无 warm-up，P95 99ms、P99 289ms 高于后续轮次，保留为冷启动/连接池预热现象，不判为拐点。80 QPS 的 P99 241ms 是单次尾部尖峰（P95 50ms），也不会判为拐点。

**与点赞的差异**：点赞 200 QPS 探针出现 35 个 HTTP 503、P95 3,106ms，吞吐只有 189.002；取消点赞 200 QPS 探针 0 错误、P95 43ms、实际 208.563。原因是首次点赞需要 Redis 初始化、Redisson 锁和活跃用户集合复制，而取消点赞只做 Lua 移除和 dirty 标记，路径明显更轻。因此两项阈值必须分别确定。

### D.5（Topology B）最终阈值（community-unlike）

**community-unlike = 160 QPS/实例**。理由：取消点赞最后测试的稳定点是 200 QPS（实际 208.563，P95 43ms，P99 148ms，0 错误），阈值取其 80%（200 → 160）；160 QPS 位于实测稳定区间内，相对 208.563 的观测稳定吞吐保留约 23% 余量，也低于 `like_relation` 写入、Redis 移除和每 5 秒 flush 共同占用的共享资源上限。没有沿用 Topology A 的 8 QPS；没有机械照搬 like 的 120（取消点赞独立实测到 208.563 仍稳定，120 会丢弃可验证的容量）；也没有取 200（贴着最后稳定点、无余量，且 250/300 QPS 未测，无法证明 200 是峰值）；本轮未继续向上探针，因此 160 是对未知上限的保守取值。阈值是每个 community JVM 的本地 FlowRule，不是集群总配额。

两项最终一起写入 Nacos `campustrain-dev`、group `CAMPUS_TRAIN`、DataId `community-service-sentinel-flow-dev.json` 与本地文件，并读回校验：

```json
[
  {"resource":"community-like","limitApp":"default","grade":1,"count":120,"strategy":0,"controlBehavior":0},
  {"resource":"community-unlike","limitApp":"default","grade":1,"count":160,"strategy":0,"controlBehavior":0}
]
```

### D.6（Topology B）开启 Sentinel 后点赞复测

2026-11-02 13:33–13:39，Nacos 与本地 `community-service-sentinel-flow-dev.json` 均为 `community-like=120`、`community-unlike=160`，读回 exactMatch=True。点赞复测使用纯净问题 199000007–199000010 与从未点赞的基准用户，负载为 0.75T / 1.0T / 1.5T / 2.0T（90 / 120 / 180 / 240 QPS），时长 60 / 60 / 55 / 42 秒，保证发送样本不超过 11,000 个唯一用户：

| 负载 | 线程 | 样本 | 实际 QPS | 200 | 429 | 429 占比 | 成功 QPS | 总 P95 ms | 成功 P95 ms | 总 P99 ms | 503 | timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 90（0.75T） | 120 | 5,440 | 91.985 | 5,436 | 4 | 0.07% | 91.917 | 27 | 27 | 44 | 0 | 0 |
| 120（1.0T） | 160 | 7,254 | 122.706 | 6,932 | 322 | 4.44% | 117.259 | 30 | 30 | 52 | 0 | 0 |
| 180（1.5T） | 240 | 9,980 | 184.456 | 6,549 | 3,431 | 34.38% | 121.042 | 38 | 42 | 65 | 0 | 0 |
| 240（2.0T） | 320 | 10,148 | 247.373 | 4,923 | 5,225 | 51.49% | 120.006 | 90 | 107 | 221 | 0 | 0 |

成功吞吐在 1.5T–2.0T 下稳定在 120.0–121.0 QPS，与 120 QPS 阈值一致；超额请求全部以 HTTP 429 返回，没有 503、5xx、timeout、connection refused/reset。1.0T 轮 322 个 429（4.44%）与 0.75T 轮 4 个 429（0.07%）是 Sentinel 1 秒统计窗口与 JMeter ConstantThroughputTimer 秒内突发叠加的窗口效应，不代表阈值低于目标；2.0T 轮成功 P95 107ms、P99 233ms，仍无错误。四个目标刷盘后：Redis count = Redis users = MySQL `like_count` = 活跃 `like_relation` 数，分别为 5,436 / 6,932 / 6,549 / 4,923，重复活跃用户 0，dirty set 每轮 2–4 秒清零。

### D.7（Topology B）开启 Sentinel 后取消点赞复测

2026-11-02 13:40–14:33，保持 `community-unlike=160` 不变。每轮先在 flow 关闭状态通过真实 `/community/like` 预置 seed 个活跃用户（7,610 / 9,290 / 10,634 / 10,130），等 dirty 清零并读回 flow 后再执行取消负载，token 全部来自本轮预置的活跃用户，且发送样本数小于 seed，避免 changed=false 快路径。负载为 0.75T / 1.0T / 1.5T / 2.0T（120 / 160 / 240 / 320 QPS），时长 60 / 55 / 42 / 30 秒：

| 负载 | 线程 | 样本 | 实际 QPS | 200 | 429 | 429 占比 | 成功 QPS | 总 P95 ms | 成功 P95 ms | 总 P99 ms | 503 | timeout |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 120（0.75T） | 160 | 7,261 | 122.789 | 7,259 | 2 | 0.03% | 122.755 | 32 | 32 | 52 | 0 | 0 |
| 160（1.0T） | 220 | 8,873 | 164.178 | 8,368 | 505 | 5.69% | 154.834 | 36 | 37 | 58 | 0 | 0 |
| 240（1.5T） | 320 | 10,151 | 247.374 | 6,611 | 3,540 | 34.87% | 161.106 | 37 | 45 | 103 | 0 | 0 |
| 320（2.0T） | 420 | 9,668 | 333.690 | 4,640 | 5,028 | 52.01% | 160.149 | 49 | 71 | 145 | 0 | 0 |

成功吞吐在 1.5T–2.0T 下为 160.1–161.1 QPS，与 160 QPS 阈值一致；超额请求全部 429，0 个 503/timeout/connection error。每轮 `after - before` 精确等于 `-http200`（-7,259 / -8,368 / -6,611 / -4,640），四个目标最终 Redis count = Redis users = MySQL `like_count` = 活跃 `like_relation` 数，分别为 351 / 922 / 4,023 / 5,490，重复活跃用户 0，dirty set 每轮 2–4 秒清零。120 QPS 轮 2 个 429（0.03%）与 160 QPS 轮 505 个 429（5.69%）同样是 1 秒窗口突发效应。

### D.8（Topology B）最终一致性、健康与回退

- 全部 15 个社区基准目标（199000001–199000015）压测后校验：`Redis users = Redis count = MySQL question.like_count = MySQL status=1 like_relation 数` 全部成立，`badTargets=[]`，重复活跃用户 0，dirty set 0，未出现负数或重复活跃关系。最终活跃数依次为 578 / 840 / 170 / 579 / 450 / 11,000 / 5,436 / 6,932 / 6,549 / 4,923 / 598 / 351 / 922 / 4,023 / 5,490。
- 业务 smoke：使用基准用户 1992001 对问题 199000001 依次执行 `GET /community/get/question/199000001`、`GET /community/get/like-status`、`POST /community/like`、`POST /community/unlike`，均 HTTP 200、code 200；like_count 578 → 579 → 578，`likedByMe` false → true → false，`changed` 均为 true，证明两条真实写路径正常。
- 健康检查：`http://192.168.10.102/nginx-health` 200、Nacos readiness 200、Sentinel Dashboard `http://192.168.10.102:8858/` 200、Gateway `/actuator/health` 200。Stage D 没有重建、重启或删除任何容器，没有修改 community 业务代码、数据库结构或其它 Sentinel 资源。
- 数据范围：只新增专用基准问题 199000006–199000015 和基准用户 1992001–2003000 的点赞/取消关系；未执行 TRUNCATE、无 WHERE 的 DELETE 或全表清理，未触碰非基准业务数据。
- 回退方式：将 Nacos 与本地 `config/nacos/community-service-sentinel-flow-dev.json` 同时发布为 `[]` 即关闭两条 community 流控；恢复时重新发布上面的 120/160 规则。阈值是每个 community JVM 的本地规则，不是集群总配额。

### D.9（Topology B）限制

- `collect_stats.py` 依赖 `VM_PASSWORD` 建立 SSH 采样，本轮后台环境未提供该变量，因此没有整机 CPU、容器 CPU、load average 和 Swap 采样，输出为 `Set VM_PASSWORD for SSH access`；本文不补填推测值，容量结论基于实际吞吐、P95/P99、错误码、dirty set 与 MySQL 一致性。
- 取消点赞只测到 200 QPS 目标（实际 208.563 仍稳定），250/300 QPS 未测，无法证明 200 是取消路径的峰值；160 是对未知上限的保守取值。
- 点赞与取消点赞阈值分别按单资源裸容量确定，未做 like+unlike 混合流量测试。两条规则各自允许 120/160 QPS，理论上可同时放行 280 QPS，超过本轮观测的单路径稳定吞吐；若生产存在显著混合写流量，必须先补混合压测再决定是否下调任一规则。
- 结论来自 4 vCPU / 7,803 MiB、单 community 实例、单 Gateway、共享 14 容器 VM 和 11,000 用户令牌池；机器规格、实例数、用户规模、Redis/MySQL 参数或 flush 参数变化后必须重测。Topology A 的 8/8 结论只适用于 2 vCPU / 3.7 GiB 资源受限环境，不再作为当前阈值依据。

### E.1（Topology B）环境、目标与口径

2026-11-02 14:41–15:07 实测裸容量。入口固定为 `POST http://192.168.10.102/video/create/upload-url` 与 `GET http://192.168.10.102/lesson/get/play-url/199001`，链路 Nginx → Gateway → training-service，单 training 实例。两类请求都只生成 OSS 预签名 URL：上传接口只返回 objectKey 和 uploadUrl，不向 OSS PUT 任何字节；播放接口只对已绑定对象生成 GET 签名，不下载对象。未上传、未删除、未修改任何 OSS 对象，未改变 AccessKey 读取方式（仅 `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET`）。

使用专用基准用户 1992001 通过真实 `/auth/login` 重新签发的 JWT（2 小时有效），令牌只写入 Git 忽略的 `scripts/sentinel-benchmark/results/tokens/`。裸容量前把 Nacos `campustrain-dev / CAMPUS_TRAIN / training-service-sentinel-flow-dev.json` 临时发布为仅保留 `training-detail=800`、`training-enrollment-check=750`，移除 `oss-upload-url`、`oss-play-url` 两条 flow；`training-service-sentinel-degrade-dev.json` 保持不变（`oss-play-url` 异常比例 0.5、timeWindow 10s、minRequestAmount 5、statIntervalMs 1000ms）。

JMeter 5.6.3 CLI，每轮 60 秒、ramp 5 秒，每轮间隔 30 秒；warm-up 20 QPS / 15 秒，不计入结论。upload 与 play 分别在 training-service 上串行压测，不并发混合。实际 QPS、P90/P95/P99 按 JTL 全量样本计算；稳定性判据同前：实际吞吐 ≥ 目标 95%、无 5xx、无 timeout/connection refused/reset、P95 不明显跳升、整机不持续打满。`collect_stats.py` 的 SSH 采样仍因 `VM_PASSWORD` 缺失不可用，CPU/load/Swap 数据为空，不补填推测值。

### E.2（Topology B）upload-url 裸容量（Sentinel flow 关闭）

| 目标 QPS | 线程 | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 / 429 / 5xx / timeout / conn error |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 20（warm-up） | 30 | 277 | 22.269 | — | 116.9 | 270 | 531 | 595 | 602 | 0 | 277 / 0 / 0 / 0 / 0 |
| 50 | 80 | 2,914 | 51.293 | 102.6% | 60.7 | 170 | 356 | 598 | 999 | 0 | 2,914 / 0 / 0 / 0 / 0 |
| 100 | 160 | 5,950 | 102.494 | 102.5% | 49.5 | 123 | 257 | 501 | 861 | 0 | 5,950 / 0 / 0 / 0 / 0 |
| 200 | 300 | 10,907 | 197.440 | 98.7% | 331.3 | 922 | 1,722 | 2,670 | 4,791 | 0 | 10,907 / 0 / 0 / 0 / 0 |
| 400 | 500 | 21,854 | 392.296 | 98.1% | 241.4 | 554 | 1,131 | 2,603 | 4,439 | 0.055 | 21,842 / 0 / 0 / 0 / 12 |
| 600 | 700 | 22,231 | 436.698 | 72.8% | 879.9 | 1,929 | 3,431 | 5,782 | 10,841 | 25.640 | 16,531 / 0 / 32 / 0 / 5,668 |
| 800 | 900 | 36,612 | 669.287 | 83.7% | 671.3 | 1,661 | 2,263 | 4,274 | 11,603 | 39.181 | 22,267 / 0 / 15 / 0 / 14,330 |

**结论**：50 与 100 QPS 是稳定点，实际 51.293 / 102.494，P95 356 / 257ms，0 错误；50 QPS 的 P95 高于 100 QPS，是 warm-up 后首轮连接池/JIT 尚未完全稳定，保留但不判为拐点。200 QPS 实际 197.440（98.7%）且 0 错误，但 Avg 从 49.5ms 升到 331.3ms、P95 从 257ms 跳到 1,722ms、P99 2,670ms，属于明显排队拐点，不满足“P95 不明显跳升”。400 QPS 出现 12 个 `NoHttpResponseException`（0.055%），已违反“无 connection error”；600/800 QPS 出现大量 SocketException、NoHttpResponse、500 与 504，实际吞吐回落到 436.698 / 669.287，明确进入饱和。最后稳定点是 100 QPS；稳定吞吐峰值 102.494 QPS。600 的 Avg 高于 800 是失败连接快速返回造成的统计现象，不表示 800 更稳定。

### E.3（Topology B）play-url 裸容量（Sentinel flow 关闭）

| 目标 QPS | 线程 | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | Error% | 200 / 429 / 5xx / timeout / conn error |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 20（warm-up） | 30 | 277 | 22.531 | — | 96.5 | 177 | 508 | 533 | 541 | 0 | 277 / 0 / 0 / 0 / 0 |
| 50 | 80 | 2,920 | 51.442 | 102.9% | 33.7 | 33 | 88 | 588 | 758 | 0 | 2,920 / 0 / 0 / 0 / 0 |
| 100 | 160 | 5,867 | 102.284 | 102.3% | 42.2 | 67 | 115 | 736 | 1,058 | 0 | 5,867 / 0 / 0 / 0 / 0 |
| 200 | 300 | 11,523 | 201.458 | 100.7% | 152.5 | 446 | 942 | 1,689 | 2,708 | 0 | 11,523 / 0 / 0 / 0 / 0 |
| 400 | 500 | 21,705 | 382.501 | 95.6% | 448.0 | 1,171 | 1,563 | 2,682 | 3,942 | 0.221 | 21,657 / 0 / 0 / 0 / 48 |
| 600 | 700 | 29,960 | 532.546 | 88.8% | 715.7 | 1,634 | 2,147 | 3,687 | 8,616 | 21.205 | 23,607 / 0 / 0 / 0 / 6,353 |
| 800 | 900 | 37,404 | 662.534 | 82.8% | 718.0 | 1,804 | 2,323 | 3,858 | 8,335 | 42.306 | 21,580 / 0 / 0 / 0 / 15,824 |

**结论**：50 与 100 QPS 稳定，实际 51.442 / 102.284，P95 88 / 115ms，0 错误。200 QPS 实际 201.458（100.7%）且 0 错误，但 Avg 42.2 → 152.5ms、P95 115 → 942ms、P99 736 → 1,689ms，出现明显排队，是第一个拐点。400 QPS 虽然实际 382.501（95.6%），但 P95 1,563ms，并出现 48 个连接异常（0.221%），不满足稳定性判据；600/800 QPS 连接异常和吞吐回落进一步确认饱和。最后稳定点是 100 QPS；稳定吞吐峰值 102.284 QPS。play-url 的 200 QPS 退化比 upload-url 稍轻（P95 942ms 对 1,722ms），但两者拐点位置一致，均未达到可接受稳定标准。

### E.4（Topology B）最终阈值（oss-upload-url / oss-play-url）

**oss-upload-url = 80 QPS/实例**。最后稳定点 100 QPS（实际 102.494，P95 257ms，0 错误），阈值取 80%（100 → 80）；80 QPS 位于 50–100 的稳定平台内，距离 100–200 的排队拐点有 20% 目标余量，也远低于 400 QPS 起出现的连接异常区。没有沿用 Topology A 的 8 QPS；没有取 100（贴着稳定点、无余量）；也没有取 120/160（已进入 P95 942–1,722ms 的排队区）。

**oss-play-url = 80 QPS/实例**。最后稳定点同为 100 QPS（实际 102.284，P95 115ms，0 错误），阈值取 80%；200 QPS 时 P95 942ms、400 QPS 起出现连接异常，80 QPS 留出与 upload 相同的 20% 目标余量。两条规则数值相同是两次独立裸容量都落在 100 QPS 稳定点的结果，不是机械统一；upload 与 play 分别有独立的 50/100/200/400/600/800 数据。阈值是每个 training JVM 的本地 FlowRule，不是集群总配额；旧 Stage10 规则 `training-detail=800`、`training-enrollment-check=750` 保持不变。

已写入 Nacos `campustrain-dev`、group `CAMPUS_TRAIN`、DataId `training-service-sentinel-flow-dev.json` 与本地文件，并读回 exactMatch=True：

```json
[
  {"resource":"training-detail","limitApp":"default","grade":1,"count":800,"strategy":0,"controlBehavior":0},
  {"resource":"training-enrollment-check","limitApp":"default","grade":1,"count":750,"strategy":0,"controlBehavior":0},
  {"resource":"oss-upload-url","limitApp":"default","grade":1,"count":80,"strategy":0,"controlBehavior":0},
  {"resource":"oss-play-url","limitApp":"default","grade":1,"count":80,"strategy":0,"controlBehavior":0}
]
```

### E.5（Topology B）开启 Sentinel 后复测

2026-11-02 15:10–15:30，Nacos 与本地 `training-service-sentinel-flow-dev.json` 均为 `oss-upload-url=80`、`oss-play-url=80`，读回一致。按 0.75T / 1.0T / 1.5T / 2.0T（60 / 80 / 120 / 160 QPS）复测，每轮 60 秒、ramp 5 秒、轮次间隔 30 秒；成功 QPS 只统计 HTTP 200 且业务 `code=200` 的样本。

**oss-upload-url**

| 负载 | 线程 | 样本 | 实际 QPS | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | 200 | 429 | 503 | timeout / 连接异常 | 成功 QPS | 成功 P95 ms | 成功 P99 ms | Error% |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| 60 | 100 | 3,216 | 59.003 | 255.1 | 456 | 1,852 | 3,633 | 4,981 | 2,626 | 590 | 0 | 0 / 0 | 48.178 | 1,938 | 3,817 | 18.346 |
| 80 | 160 | 4,540 | 82.977 | 102.7 | 226 | 471 | 1,170 | 1,533 | 2,907 | 1,633 | 0 | 0 / 0 | 53.131 | 383 | 815 | 35.969 |
| 120 | 240 | 7,170 | 123.819 | 36.7 | 83 | 160 | 448 | 922 | 4,644 | 2,526 | 0 | 0 / 0 | 80.198 | 152 | 419 | 35.230 |
| 160 | 320 | 9,203 | 165.029 | 82.4 | 152 | 285 | 1,156 | 1,946 | 4,459 | 4,744 | 0 | 0 / 0 | 79.959 | 213 | 1,079 | 51.548 |

**oss-play-url**

| 负载 | 线程 | 样本 | 实际 QPS | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | 200 | 429 | 503 | timeout / 连接异常 | 成功 QPS | 成功 P95 ms | 成功 P99 ms | Error% |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| 60* | 100 | 3,023 | 59.536 | 169.6 | 165 | 686 | 3,324 | 3,632 | 2,575 | 448 | 0 | 0 / 0 | 50.713 | 852 | 3,408 | 14.820 |
| 80 | 160 | 4,647 | 82.233 | 79.1 | 150 | 313 | 1,316 | 1,527 | 3,215 | 1,432 | 0 | 0 / 0 | 56.893 | 283 | 1,298 | 30.816 |
| 120 | 240 | 6,792 | 121.262 | 128.7 | 101 | 582 | 3,175 | 3,842 | 4,444 | 2,348 | 0 | 0 / 0 | 79.342 | 582 | 3,351 | 34.570 |
| 160* | 320 | 8,338 | 151.405 | 518.9 | 1,767 | 3,249 | 5,707 | 9,644 | 4,091 | 4,247 | 0 | 0 / 0 | 74.286 | 4,158 | 7,128 | 50.935 |

\* play 60 目标的样本窗口为 50.776 秒、play 160 为 55.071 秒；JMeter 调度尾段没有新请求，QPS 按实际首末样本窗口计算，不与 60 秒窗口轮次直接比较。

**429 的来源是负载发生器的秒内突发，不是规则低于目标**。按 JTL 首请求时间戳统计每秒实际发出量：upload 在 60 目标下中位 56/s、最大 100/s，36.4% 的秒超过 80；80 目标下中位 120/s、最大 160/s，50.0% 的秒超过 80；120 目标下中位 139/s、最大 193/s，98.3% 超过 80；160 目标下中位 182/s、最大 287/s，100% 超过 80。play 在 60 目标下中位 54/s、最大 100/s；80 目标下中位 131/s、最大 134/s；120 目标下中位 110/s、最大 204/s；160 目标下中位 153/s、最大 320/s。ConstantThroughputTimer 在低目标速率下不是匀速发送，而是“秒内突发 + 空档”，Sentinel 的 1 秒窗口只放行每个窗口内的 80 个请求，因此 60/80 目标仍会出现 429；120/160 目标下几乎每秒都超过 80，成功吞吐稳定封顶在 79.3–80.2 QPS（play 160 异常轮除外）。

**平滑负载对照**。为区分“规则错误”和“发生器突发”，用较少线程重跑 80 目标：

| 资源 | 目标 | 线程 | 样本 | 实际 QPS | 200 | 429 | 503 | timeout / 连接异常 | 成功 QPS | 总 P95 ms | 成功 P95 ms | 总 P99 ms | Error% |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: | ---: |
| upload | 80 | 24 | 4,175 | 75.755 | 4,068 | 107 | 0 | 0 / 0 | 73.813 | 69 | 73 | 1,054 | 2.563 |
| play | 80 | 16 | 4,249 | 73.007 | 4,225 | 24 | 0 | 0 / 0 | 72.595 | 256 | 258 | 664 | 0.565 |

两轮实际发出量只有 73–76 QPS（低线程数下 ConstantThroughputTimer 也未能精确压到 80），429 仅 2.56% / 0.57%，证明 80 QPS 规则在平滑流量下不会大面积拒绝；前面 30–36% 的 429 是发生器秒内突发的产物。JTL 按自然秒统计的 200 数在突发轮次可到 100–147/s：Sentinel 使用 1 秒滑动窗口（默认 2 个 500ms 桶），自然秒可跨两个窗口边界，滑动窗口内的放行量仍受 80 限制，这不是规则失效。

**异常轮次（保留，不用于阈值判断）**：
- upload 60 目标是规则发布后的首轮，成功 P95 1,938ms，明显高于后续 80/120/160 轮的 383/152/213ms；该轮 36.4% 的秒突发超过 80，叠加冷启动/连接池预热，保留为异常轮次。
- play 160 目标成功 QPS 74.286、成功 P95 4,158ms、429 占 50.9%，0 个 503/timeout；秒级发出量最大 320/s、自然秒放行峰值 139/s。OSS 客户端按请求创建/关闭，突发下排队明显，该轮不作为容量结论。
- 全部 enabled 轮次 0 个 503、0 timeout、0 connection refused/reset、0 其它连接错误；429 全部来自 `FlowException`。

本轮 enabled 复测同样没有 CPU/load/Swap 采样：`collect_stats.py` 依赖 `VM_PASSWORD` 建立 SSH，本轮环境未提供该变量，输出为 `Set VM_PASSWORD for SSH access`。不补填推测值；容量结论基于实际吞吐、P95/P99、错误码和一致性数据。

### E.6（Topology B）oss-play-url degrade 修复与受控注入

**设计缺陷**：`LessonController#getLessonPlayUrl` 原 `@SentinelResource` 没有 `exceptionsToIgnore`，Sentinel 会把 `BusinessException`（课时不存在、非视频课时、课时未绑定视频）计入异常比例。无效 `lessonId` 是客户端/数据校验错误，不是 OSS 系统故障；Topology A 的 E.6 实验正是用无效 `lessonId` 在 8 次并发后打开了熔断，正常请求随后 503，说明该口径会把业务错误放大成可用性故障。

**修复**：`@SentinelResource(value = "oss-play-url", blockHandler = "handleOssPlayUrlBlock", exceptionsToIgnore = BusinessException.class)`。业务异常不再进入 Sentinel 的异常统计，仍由全局异常处理按原契约返回 `code=400`；只有凭据缺失、签名失败等系统异常才参与异常比例熔断。HTTP 接口、响应结构、事务和 AccessKey 读取方式（仅 `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET`）均未改变。

**受控故障注入**：新增 `OssFaultProperties`（`campustrain.oss.fault.enabled=false`、`campustrain.oss.fault.play-url-exception=false`，默认关闭，`@RefreshScope` 可在 Nacos 动态打开/关闭）。`OssServiceImpl.generatePlayUrl` 在两个开关都打开时、调用 OSS 签名前抛出 `IllegalStateException("Injected OSS play URL system fault")`；不抛 `BusinessException`，不写文件、不创建或删除 OSS 对象，不改变 AccessKey。Nacos `training-service-dev.yaml` 与本地 `config/nacos/training-service-dev.yaml` 同步了默认关闭的配置块。

**单元/集成测试**：`mvn -pl training-service test -Dtest=OssSentinelTest,OssFaultInjectionTest -DfailIfNoTests=false` 实测 `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`（`OssSentinelTest` 6 项、`OssFaultInjectionTest` 2 项）。新增用例：

- `businessExceptionDoesNotOpenPlayUrlDegrade`：异常比例规则（count=0.1、minRequestAmount=1、statIntervalMs=1000）下连续 3 次 `BusinessException`，等待 1.1 秒后再 3 次仍为 `BusinessException`，没有被 503 拦截，证明业务异常不进入统计。
- `systemExceptionOpensPlayUrlDegrade`：1 次 `IllegalStateException` 后等待 1.1 秒，有效请求返回 HTTP 503、`"视频服务暂时不可用，请稍后重试"`，证明系统异常会打开熔断。
- `OssFaultInjectionTest`：默认开关为 false；打开后 `generatePlayUrl` 在 OSS 调用前抛出 `IllegalStateException`。
- 既有 `flowBlockedPlayUrlReturnsTooManyRequests`、`flowBlockedUploadUrlReturnsTooManyRequests`、`degradeBlockedPlayUrlReturnsVideoServiceUnavailable`、`degradeBlockedUploadUrlReturnsVideoServiceUnavailable` 继续验证 `FlowException → 429`、其它 Block → 503。

**运行态 A/B 已完成（2026-11-02 16:22–16:28，Topology B）**：先运行 `mvn -pl training-service test`（36 项全部通过，BUILD SUCCESS）和 `mvn -pl training-service clean package -DskipTests`（BUILD SUCCESS）。通过已授权的 VM SSH，仅把最新 JAR 传到 `/opt/campustrain/training-service/target/`，校验 SHA-256，保留旧 JAR 备份；执行 `docker compose build training-service`、`docker compose up -d --no-deps training-service`。training-service 重新变为 healthy，其余容器未重建。全程通过 `http://192.168.10.102/` 的 Nginx → Gateway 入口，使用有效 JWT；未运行容量压测。

- **A（业务异常不触发 degrade）**：Nacos 与 Git 的 OSS fault 开关均为 false。对不存在的 `lessonId=199999999` 连续并发请求 10 次，全部 HTTP 200、响应体 `code=400`；随后立即请求有效视频 `lessonId=199080`，HTTP 200、`code=200`，正常生成 `playUrl`。合法请求未被 503/`DegradeException` 拦截。完整预签名 URL 和 JWT 未记录。
- **B（系统异常触发 degrade）**：仅在 Nacos 的 `campustrain-dev / CAMPUS_TRAIN / training-service-dev.yaml` 临时将 `campustrain.oss.fault.enabled` 与 `play-url-exception` 设为 true，观察到有效课时请求返回 HTTP 200、响应体 `code=500`（`IllegalStateException` 被全局异常处理封装），确认动态配置已刷新。随后 10 次受控并发请求中 9 次为该系统异常、1 次为 HTTP 503；紧接着 3 次请求均为 HTTP 503、响应体 `code=503` 和“视频服务暂时不可用，请稍后重试”。training-service 日志记录 `resource=oss-play-url`、`blockType=DegradeException`。
- **恢复与最终配置**：在 `finally` 中发布回原始 Nacos 内容，等待 12 秒（超过 `timeWindow=10s`），有效课时连续 3 次均为 HTTP 200、`code=200`，均生成 `playUrl`。最终再次读回 Nacos，内容与 Git 的 `config/nacos/training-service-dev.yaml` SHA-256 完全一致；两个 fault 开关均为 false。`docker compose ps` 显示 14/14 容器 healthy；Nginx `/nginx-health`、Nacos readiness 均 HTTP 200，Gateway `/actuator/health` 为 `UP`。未修改任何 flow/degrade 阈值、数据库、OSS 对象或 AccessKey。

### E.7（Topology B）六项资源 enabled 最终高负载验收

2026-11-02 15:54–16:06，六条 flow 规则全部启用，Nacos 与本地文件一致。入口继续为 `http://192.168.10.102/`，链路 Nginx → Gateway → 业务服务；每轮 60 秒、ramp 5 秒、轮次间隔 30 秒（community-like 与 unlike 之间额外完成预置用户修复）。成功 QPS 只统计 HTTP 200 且业务 `code=200` 的样本。六轮的目标都高于对应阈值，用于验证“超量被拦截、成功吞吐被阈值封顶”：

| 资源 | 目标 QPS | 线程 | 样本 | 实际 QPS | 达标率 | Avg ms | P90 ms | P95 ms | P99 ms | Max ms | 200 | 429 | 503 | timeout / 连接异常 | 成功 QPS | 成功 P95 ms | 成功 P99 ms | Error% |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: | ---: |
| auth-login | 30 | 30 | 1,712 | 29.879 | 99.6% | 167.7 | 490 | 696 | 1,394 | 3,203 | 458 | 1,254 | 0 | 0 / 0 | 7.993 | 1,247 | 2,634 | 73.248 |
| learning-progress-report | 160 | 320 | 7,641 | 139.289 | 87.1% | 1,083.7 | 2,759 | 4,802 | 8,277 | 9,025 | 3,516 | 4,125 | 0 | 0 / 0 | 64.117 | 7,152 | 8,602 | 53.985 |
| community-like | 240 | 320 | 12,927 | 236.884 | 98.7% | 130.4 | 230 | 479 | 2,349 | 3,088 | 6,419 | 6,508 | 0 | 0 / 0 | 117.627 | 538 | 2,469 | 50.344 |
| community-unlike | 200 | 320 | 10,385 | 202.425 | 101.2% | 190.5 | 411 | 813 | 2,464 | 3,123 | 6,511 | 3,874 | 0 | 0 / 0 | 126.913 | 891 | 2,649 | 37.304 |
| oss-upload-url | 120 | 240 | 6,928 | 121.802 | 101.5% | 100.3 | 106 | 547 | 1,940 | 2,712 | 4,480 | 2,448 | 0 | 0 / 0 | 78.764 | 407 | 1,765 | 35.335 |
| oss-play-url | 120 | 240 | 6,923 | 124.073 | 103.4% | 399.2 | 771 | 1,060 | 1,318 | 1,763 | 3,113 | 3,810 | 0 | 0 / 0 | 55.791 | 1,111 | 1,537 | 55.034 |

**共同结论**：六项资源在高于阈值的负载下全部满足保护目标——成功吞吐均未超过对应阈值（auth 7.993 ≤ 8、learning 64.117 < 80、like 117.627 < 120、unlike 126.913 < 160、upload 78.764 < 80、play 55.791 < 80），超额请求全部为 HTTP 429，0 个 503、0 个 5xx、0 timeout、0 connection refused/reset。没有出现“把 429 伪装成成功”或“阈值未生效”的情况。

**逐项说明**：

- auth-login：30 QPS 目标实际发出 29.879，458 个 200、1,254 个 429，成功 7.993 QPS，与 8 QPS 阈值一致。成功 P95 1,247ms 是 30 QPS 下 BCrypt 与 4 vCPU 竞争的结果；2026-11-02 11:54 的 B.4 轮在最高 16 QPS 下成功 P95 为 585–828ms，本轮目标更高，延迟上升属于过载排队，不是超时或错误。
- learning-progress-report：目标 160 QPS 下 JMeter 只发出 139.289 QPS，3,516 个 200、4,125 个 429，成功 64.117 QPS，低于 80 阈值；成功 P95 7,152ms。该轮与 C.5 的 160 QPS enabled 轮（成功 80.277 QPS、成功 P95 122ms）明显不同，属于**保留的异常轮次**：320 线程高并发 + 生成器秒内突发使请求在服务端排队，成功吞吐被压到 64 QPS；没有 5xx/503/timeout，流控仍把超量请求全部转为 429。由于缺少 CPU/线程采样，不能把原因精确归因到 CPU、Jetty 队列或 Feign/Redis 之一；该轮不用于容量结论，C.2/C.5 仍是 learning 阈值的依据。
- community-like：目标 240 QPS 实际 236.884，成功 117.627 QPS，贴合 120 阈值；成功 P95 538ms、P99 2,469ms，429 占 50.3%，0 个 503。本轮使用 1,999 个最终验收用户令牌；首次点赞后同一用户会被 CSV 回收再次请求，因此后段包含幂等 `changed=false`，该轮只验证高负载保护，首次点赞容量仍以 D.2/D.6 的 11,000 用户、逐请求唯一 token 结果为准。
- community-unlike：目标 200 QPS 实际 202.425，成功 126.913 QPS（低于 160 阈值），429 占 37.3%，0 个 503。取消前通过真实 `/community/like` 把 1,999 个用户全部预置为活跃状态（`likedAfterRepair=1999`、`stillMissing=0`），随后执行取消负载；最终问题 199000052 的活跃关系、Redis users/count、MySQL like_count 全部回到 0。与 like 相同，1,999 个用户被回收后包含幂等 `changed=false`，该轮验证保护而非重新测定 unlike 容量；unlike 阈值仍以 D.4/D.5 为准。
- oss-upload-url：目标 120 QPS 实际 121.802，成功 78.764 QPS，贴合 80 阈值；成功 P95 407ms、P99 1,765ms，0 个 503。
- oss-play-url：目标 120 QPS 实际 124.073，成功 55.791 QPS，明显低于 80 阈值，成功 P95 1,111ms；429 占 55.0%，0 个 503。原因是 240 线程的秒内突发叠加 OSS 客户端“每次请求创建/关闭”的既有实现，排队使成功吞吐低于规则上限；E.5 中用 16 线程平滑负载在 80 目标下已验证 72.595 成功 QPS、0.565% 429、0 个 503。该轮是异常轮次，不推翻 80 阈值，也不据此调整规则。

本轮同样没有 CPU/load/Swap 采样：`collect_stats.py` 依赖 `VM_PASSWORD` 建立 SSH，输出为 `Set VM_PASSWORD for SSH access`。不补填推测值。

### E.8（Topology B）最终业务回归

2026-11-02 16:07–16:08，六轮压测结束后通过网关执行完整业务回归；除首次不带 token 的 growth 探测返回 401 外，其余均为 HTTP 200 且业务 `code=200`（growth ranking 返回裸对象/数组，无 `code` 字段）：

| 检查 | 请求 | 结果 |
| --- | --- | --- |
| 登录 | `POST /auth/login`，基准账号 + BCrypt | 200，code=200 |
| 培训详情 | `GET /training/get/199001` | 200，code=200，id=199001 |
| 我的报名 | `GET /enrollment/list/mine?page=1&size=10` | 200，code=200，records=1 |
| 开始学习 | `POST /learning/start/199041` | 200，code=200 |
| 上报进度 | `POST /learning/report-progress/199041`，`progressSeconds=95` | 200，code=200，progressSeconds=95 |
| 查询进度 | `GET /learning/get-progress/199041` | 200，code=200，progressSeconds=95 |
| 培训完成度 | `GET /learning/get/training-progress/199001` | 200，code=200 |
| 总榜 | `GET /growth/get/total-ranking?limit=10` | 200，8 条 |
| 我的排名 | `GET /growth/get/my-ranking` | 200 |
| 问题详情 | `GET /community/get/question/199000052` | 200，code=200 |
| 点赞烟测 | `POST /community/like` | 200，`likedByMe=true`、`changed=true` |
| 取消点赞烟测 | `POST /community/unlike` | 200，`likedByMe=false`、`changed=true` |
| 上传 URL | `POST /video/create/upload-url` | 200，code=200，返回 URL（仅生成签名，不上传字节） |
| 播放 URL | `GET /lesson/get/play-url/199041` | 200，code=200，返回 URL（仅生成签名，不下载字节） |

首次 `GET /growth/get/total-ranking` 未带 token 返回 401，属于网关鉴权行为；带基准 token 重试后返回 200。回归没有创建/删除 OSS 对象，没有输出 AccessKey、JWT、密码或完整预签名 URL。

### E.9（Topology B）最终数据一致性

**community**。压测专用问题 199000052 初始 like_count=0。like 轮结束、dirty 清零后：MySQL like_count=1,998、活跃 `like_relation`=1,998、Redis users=1,998、Redis count=1,998、重复活跃用户=0；1 个用户在 240 QPS 下所有请求均为 429，未产生点赞，随后通过真实 `/community/like` 补齐（1 次请求、0 失败），使 1,999 个基准用户全部处于活跃状态。unlike 轮结束、dirty 清零后：MySQL like_count=0、活跃关系=0、Redis users=0、Redis count=0、重复活跃用户=0、负 like_count=0、`campustrain:community:like:dirty`=0。

对 Redis 中存在 `count` 键的 18 个 benchmark 问题逐一核对：`Redis count = Redis users = MySQL question.like_count = MySQL 活跃 like_relation 数 = 活跃关系去重用户数`，0 个不一致；`like_count<0` 的问题为 0。未执行 TRUNCATE，未执行无 WHERE 的 DELETE，只清理/使用专用基准问题与基准用户。

**learning**。最终轮使用课时 199041，1,000 个基准用户中 986 个至少获得一次 HTTP 200，14 个全部为 429，因此 MySQL 无记录；dirty 清零后：MySQL `learning_record` 986 行，Redis 进度 Hash 986 个，逐用户比较 `progressSeconds`、`durationSeconds`、`completed`，0 个不一致、0 个 MySQL 行缺 Redis 键，`campustrain:learning:dirty`=0。容量轮次的上报进度为 3–13 秒，未触发完成；E.8 回归中用户 1990100 单次上报到 95 秒（超过 90 秒完成阈值），Redis 与 MySQL 均变为 `progressSeconds=95, completed=1, status=2`，并新增 1 条 lesson 199041 的 `LEARNING_COMPLETED` 事件（该用户 lesson 199038 的 1 条事件是既有历史数据，与本轮无关）。进度字段没有回退，也没有超过 100 秒课时长度。

### E.10（Topology B）最终配置、Dashboard 与健康检查

- Nacos `campustrain-dev` / group `CAMPUS_TRAIN` 的 8 个 DataId 与本地 Git 文件逐字读回 `exactMatch=True`：`auth-service-sentinel-flow-dev.json`、`learning-service-sentinel-flow-dev.json`、`community-service-sentinel-flow-dev.json`、`training-service-sentinel-flow-dev.json`、`training-service-sentinel-degrade-dev.json`、`training-service-dev.yaml`、`campus-gateway-sentinel-flow-dev.json`、`enrollment-service-sentinel-flow-dev.json`。
- 健康检查：`http://192.168.10.102/nginx-health` 200、Nacos readiness 200、Gateway `/actuator/health` 200（status=UP）、Sentinel Dashboard 根路径 200。Sentinel Dashboard 的详细资源/规则视图需要登录，本轮没有可用登录凭据，未尝试猜测或绕过；Dashboard 根路径可达仅证明页面服务存活，规则内容的权威来源是 Nacos 读回结果。
- 本轮没有重建、重启或删除任何容器，没有修改业务代码、数据库结构、AccessKey 读取方式或 Stage10 旧阈值；`training-detail=800`、`training-enrollment-check=750`、`enrollment-create=160`、Gateway `enrollment-service=150` 保持不变。

### E.11（Topology B）限制与最终规则表

**限制**：

1. 原 Topology B 容量轮次没有向 `collect_stats.py` 提供 `VM_PASSWORD`，因此那些轮次无法采集整机 CPU、容器 CPU、load average 和 Swap，并输出 `Set VM_PASSWORD for SSH access`。本次 E.6 的 A/B 补测已通过授权 SSH 完成部署，但不能倒填历史容量轮次的 CPU 数据；容量结论仍基于当时实际吞吐、延迟分位、HTTP 状态码、429/503、dirty set 与 MySQL/Redis 一致性。
2. `oss-play-url` degrade 运行态 A/B 已于 2026-11-02 补测完成，证据与恢复结果见 E.6；此前“代码尚未部署”的限制已解除。
3. 最终验收的 community 轮次只有 1,999 个有效基准用户令牌；首次点赞/取消后 CSV 回收会产生幂等 `changed=false`。因此 E.7 的 like/unlike 轮只证明“高负载下 429 保护与最终一致性”，不替代 D.2/D.4/D.6 用约 11,000 个唯一用户完成的首次点赞/真实取消容量测定。
4. E.7 learning 轮是异常轮次（成功 64.117 QPS、成功 P95 7,152ms，0 个 503/timeout），与 C.5 同目标轮差异明显；该轮不作为阈值依据，原因受限于无 CPU/线程采样，不能精确归因。
5. Topology A（2 vCPU / 约 3.7 GiB、swap 明显）的历史数据保留在 B–E 各节前半部分，仅作为资源受限拓扑对照；当前所有阈值均以 Topology B（4 vCPU / 约 7.6 GiB、14 个 healthy 容器）实测为准，不沿用 Topology A 的 3/8/8/8/8/8。

**最终规则表（Topology B）**：

| 资源 | 服务 | 规则 | 最后稳定点 | 第一个明显拐点 | 已观测峰值/饱和区 | 最终阈值 | 依据 |
| --- | --- | --- | --- | --- | --- | ---: | --- |
| auth-login | auth-service | flow QPS | 10 QPS（10.120） | 15 QPS（CPU 92.9%/99.7%、load 10.0） | 15.150 QPS（40 目标） | 8 | B.2、B.3、B.4 |
| learning-progress-report | learning-service | flow QPS | 100 QPS（103.474） | 150 QPS（P95 1,479ms） | 193.804 QPS（200 目标） | 80 | C.2、C.4、C.5 |
| community-like | community-service | flow QPS | 150 QPS（155.374） | 200 QPS（35 个 503、P95 3,106ms） | 189.002 QPS（饱和吞吐） | 120 | D.2、D.3、D.6 |
| community-unlike | community-service | flow QPS | 200 QPS（208.563） | 未继续上探（250/300 未测） | 208.563 QPS | 160 | D.4、D.5 |
| oss-upload-url | training-service | flow QPS | 100 QPS（102.494） | 200 QPS（P95 1,722ms） | 392.296 QPS（含 12 个连接异常） | 80 | E.2、E.4、E.5 |
| oss-play-url | training-service | flow QPS | 100 QPS（102.284） | 200 QPS（P95 942ms） | 382.501 QPS（含 48 个连接异常） | 80 | E.2、E.4、E.5 |
| oss-play-url | training-service | degrade（异常比例） | — | — | — | 0.5 / 10s / 5 次 / 1000ms | E.6（代码、测试、运行态 A/B 与恢复均已验证） |
| training-detail | training-service | flow QPS | Stage10 历史值，本轮未重测 | — | — | 800 | 保持不变 |
| training-enrollment-check | training-service | flow QPS | Stage10 历史值，本轮未重测 | — | — | 750 | 保持不变 |
| enrollment-create | enrollment-service | flow QPS | Stage10 历史值，本轮未重测 | — | — | 160 | 保持不变 |
| enrollment-service（Gateway 路由） | campus-gateway | flow QPS | Stage10 历史值，本轮未重测 | — | — | 150 | 保持不变 |

所有 flow 阈值都是每个 JVM/路由实例的本地 QPS 规则，不是集群总配额。`oss-play-url` 的 `BusinessException`（课时不存在、非视频课时、课时未绑定视频）已通过 `exceptionsToIgnore` 排除在异常比例统计之外，只有真正的系统异常参与熔断；BlockHandler 仍将 `FlowException` 映射为 HTTP 429、其它 Block 映射为 HTTP 503。
