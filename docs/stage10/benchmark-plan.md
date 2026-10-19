# Stage 10 基准与治理验证

状态：已完成（2026-10-19）。实测数据见 [benchmark-results.md](benchmark-results.md)，最终阈值见 [sentinel-thresholds.md](sentinel-thresholds.md)，实际拓扑见 [topology.md](topology.md)。本文件保留方法学；Topology C 未测试。任何负载测试须在用户授权的隔离环境进行，先确认数据、拓扑、当前 Nacos 配置和回滚方案。两个故障开关默认关闭；每个实验后恢复。JMeter 5.6.3 CLI 可用，JMeter 不随仓库安装。

## 数据安全

选择专用启用 STUDENT，保证对目标草稿从未报名，目标培训保持 status=0；记录 `SELECT COUNT(*) FROM campus_enrollment.enrollment WHERE user_id=? AND training_id=?` 的前后值，必须不变。草稿使请求经过 user 与 training Feign 后安全返回 400，避免测试成功报名；脚本预检内部校验状态并要求 `-ConfirmDraft`，但不能防止并发管理员修改状态，须隔离测试数据及停止状态变更。若已有 Stage 6 数据，可选择 90002，但必须先核实存在且为草稿。不要用可报名培训做并发写入。此次不需要新增或清理测试报名，禁止 TRUNCATE。

## 命令

`run.ps1` 调用 `stage10.jmx`，需要 PowerShell 7、JMeter 5.6.3、Java 17。每次必须传实际实例数、Dashboard 状态、直连或 Gateway；脚本保存对应 topology JSON、JTL 与 HTML。`-Qps` 是整个 JMeter 线程组的目标 QPS，不保证实际达到，需看吞吐和样本数。Gateway token 通过参数提供，不写入文件；注意进程命令行可被本机用户查看。

```powershell
$common = @{ Topology='A'; GatewayInstances=1; EnrollmentInstances=1; TrainingInstances=1; UserInstances=1; MysqlInstances=1; NacosInstances=1; DashboardRunning=$false; Threads=20; Duration=60; Qps=10; TrainingId=90002 }
.\scripts\stage10\jmeter\run.ps1 @common -Resource training-detail -Access direct -Port 8082
.\scripts\stage10\jmeter\run.ps1 @common -Resource training-enrollment-check -Access direct -Port 8082
.\scripts\stage10\jmeter\run.ps1 @common -Resource enrollment-create -Access direct -Port 8083 -UserId 1 -ConfirmDraft
.\scripts\stage10\jmeter\run.ps1 @common -Resource gateway-enrollment -Access gateway -Port 8080 -UserId 1 -Token $token -ConfirmDraft
```

`-TrainingCheckUrl` 默认直连 `http://localhost:8082`，可指定真实内部 training 实例以做草稿预检。直连 enrollment 的身份 Header 只用于可信内网实验，不能代替 Gateway 鉴权。Gateway 登录取得 token 后传入；不要把 token 存 Git。JMeter 的 400 草稿业务拒绝按预期状态及消息断言成功，429/503、超时和意外 200 都计为错误。先做少量请求，核对日志包含两个 Feign 和数据库行数。

## 性能步骤

1. 设置拓扑 A，确认实例及目标，测试中暂时清空或显著调高各层 Flow Rule，同时确认详情 fault 关闭；记录原 Nacos 配置并在结束后恢复。各阶段应稳定热身，分别逐级增加 `-Qps` 和适当线程数，每级独立运行并保存报告。
2. 四个资源分别记录实际吞吐、Average、P90/P95/P99、Error%、样本数、超时和 topology；不把训练详情结果当内部校验结果。不把刻意的草稿 400 当系统错误，也不能将真实 429/503 误判成功。
3. 找吞吐增长趋缓、尾延迟显著上升或错误增加的拐点；确定此前最高稳定区间，在其内留 20%~30% 余量，分别为每实例资源选值。不要用最大 QPS 直接乘 0.8。再将规则发布 Nacos，重复同样负载，比较吞吐、尾延迟、Block、超时和数据库记录。没有对照数据不得声称更稳定。
4. 拓扑 B 使用三个 training 实例，经 Gateway + LoadBalancer 测详情和完整报名链，记录集群总吞吐、各实例 `training enrollment check` / `training detail queried` 日志分布、P95/P99 和错误率。内部端点不经 Gateway 暴露，单独直连每实例测试。C 仅在报名端确有瓶颈时使用。
5. 测试慢调用、异常比例时仅更改 training-service-dev.yaml 的故障开关，按 README 关闭并等待窗口后验证恢复。停全部 training 或 user，等待发现和缓存更新，验证 Feign 503、无落库，再恢复服务并确认链路恢复。

演示阈值已由实测替代：最终值 800/750/160/150 已回写 Nacos，依据和原始统计见 `benchmark-results.md` 与 `sentinel-thresholds.md`。原始 JTL/HTML 保存在 `scripts/stage10/jmeter/results/` 受控位置；不要提交 token 或个人数据。Topology C 与 Gateway 多实例入口预算仍需后续单独实测。
