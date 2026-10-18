# Stage 10 压测结果

BENCHMARK_PENDING：尚未完成可复现的逐级基准和治理对照；不填写未经实测的吞吐、百分位或扩容倍数。Topology A/B 定义见 topology.md。

| Resource | Topology | Instances | Stable QPS | Knee QPS | Final Sentinel QPS | Scope |
| --- | --- | --- | --- | --- | --- | --- |
| training-detail | A | training=1 | BENCHMARK_PENDING | BENCHMARK_PENDING | BENCHMARK_PENDING | 每实例 |
| training-detail | B | training=3 | BENCHMARK_PENDING | BENCHMARK_PENDING | BENCHMARK_PENDING | 集群实测 / 本地规则 |
| training-enrollment-check | A | training=1 | BENCHMARK_PENDING | BENCHMARK_PENDING | BENCHMARK_PENDING | 每实例 |
| enrollment-create | A | enrollment=1 | BENCHMARK_PENDING | BENCHMARK_PENDING | BENCHMARK_PENDING | 每实例 |
| Gateway enrollment | A/B | gateway=1 | BENCHMARK_PENDING | BENCHMARK_PENDING | BENCHMARK_PENDING | 单 Gateway 入口预算 |

每次附时间、目标端口/路由、Gateway/enrollment/training/user/MySQL/Nacos 实例数、Dashboard 状态、直连或 Gateway、目标 QPS/线程数、实际吞吐、Average、P90/P95/P99、Error%、样本数、超时、流控规则和数据行数。记录基线与治理后两组结果及其差异，实际完成前不能推断稳定性改善。

## 脚本冒烟（不是性能基准）

2026-10-18 23:43，本机 JMeter 5.6.3，直连旧版 training `localhost:8082/trainings/189`，2 线程、10 秒、目标 1 QPS：11 个样本、断言错误 0。执行时观察到 Gateway=1、enrollment=1、training=3、user=1 的本机监听进程；MySQL 与 Nacos 各连接一个已知地址，但其后端是否单实例未核实；Dashboard 未运行。此实验没有切换到拓扑 A，也未测治理前后或性能拐点，不能推导稳定 QPS、百分位或阈值。
