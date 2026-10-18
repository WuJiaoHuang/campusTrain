# Stage 10 Sentinel 阈值

所有最终阈值、稳定容量、性能拐点和安全余量均为 **BENCHMARK_PENDING**。下表数值只用于本地演示各层 Block，未经容量测量，不是最终值、容量承诺或生产建议。

| 资源 | 示例阈值 | 实际范围 | 最终阈值 | 稳定区间 / 拐点 / 安全余量 |
| --- | ---: | --- | --- | --- |
| training-detail | 5 QPS | training 每实例 | BENCHMARK_PENDING | BENCHMARK_PENDING |
| training-enrollment-check | 6 QPS | training 每实例 | BENCHMARK_PENDING | BENCHMARK_PENDING |
| enrollment-create | 3 QPS | enrollment 每实例 | BENCHMARK_PENDING | BENCHMARK_PENDING |
| enrollment-service Gateway route | 2 QPS | Gateway 每实例 | BENCHMARK_PENDING | BENCHMARK_PENDING |

这些临时值只是故意区分规则以验证路由，不代表 6 比 5 是合理优先额度。仅当独立基准证明承载能力允许，才可以给报名校验配置较高预算。最终应记录拓扑、单实例稳定区间、拐点、选择原因及余量；Gateway 多实例总预算和 training 多实例总吞吐均须另外实测，不能简单相乘。
