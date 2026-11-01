# Sentinel 新增资源压测与最终治理

## 环境与拓扑

2026-11-02 实测。压测客户端是 Windows 本机的 JMeter 5.6.3 CLI，外部业务入口是 VMware CentOS 虚拟机 `http://192.168.10.102/` 的 Nginx，再到 Gateway。未使用 Windows Docker Desktop。Nacos Discovery 实时查询显示 campus-gateway、auth-service、learning-service、community-service、training-service、enrollment-service、user-service 各 1 个健康实例；目标容器内端口分别为 8080、8084、8087、8090、8082、8083、8081。MySQL、Redis、Nacos、RabbitMQ 在同一虚拟机的 Docker Compose 基础设施中；Sentinel Dashboard `http://192.168.10.102:8858/` 实测 HTTP 200。当前是单 Gateway、单业务实例的 smoke 拓扑，不是 Stage10 旧版 Windows 本机多实例拓扑。

Stage A 时 Nacos 新资源 flow 示例文件 `auth-service-sentinel-flow-dev.json`、`learning-service-sentinel-flow-dev.json`、`community-service-sentinel-flow-dev.json` 均保持 `[]`。Stage B 结束后 `auth-service-sentinel-flow-dev.json` 已写入 auth-login=3 QPS，learning 与 community 仍为 `[]`，详见下文。本次没有改旧阈值：training-detail 800、training-enrollment-check 750、enrollment-create 160、Gateway enrollment route 150 QPS/实例。

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
