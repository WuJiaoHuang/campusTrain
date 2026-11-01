# Sentinel 新资源压测 Stage A

## 环境与拓扑

2026-11-02 实测。压测客户端是 Windows 本机的 JMeter 5.6.3 CLI，外部业务入口是 VMware CentOS 虚拟机 `http://192.168.10.102/` 的 Nginx，再到 Gateway。未使用 Windows Docker Desktop。Nacos Discovery 实时查询显示 campus-gateway、auth-service、learning-service、community-service、training-service、enrollment-service、user-service 各 1 个健康实例；目标容器内端口分别为 8080、8084、8087、8090、8082、8083、8081。MySQL、Redis、Nacos、RabbitMQ 在同一虚拟机的 Docker Compose 基础设施中；Sentinel Dashboard `http://192.168.10.102:8858/` 实测 HTTP 200。当前是单 Gateway、单业务实例的 smoke 拓扑，不是 Stage10 旧版 Windows 本机多实例拓扑。

Nacos 新资源 flow 示例文件 `auth-service-sentinel-flow-dev.json`、`learning-service-sentinel-flow-dev.json`、`community-service-sentinel-flow-dev.json` 均保持 `[]`。本次没有改旧阈值：training-detail 800、training-enrollment-check 750、enrollment-create 160、Gateway enrollment route 150 QPS/实例。

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

## 容量结果（Stage B）

留空。未做拐点或峰值测试；未确定新资源最终 Sentinel 阈值。
