# Stage 10 测试拓扑

每次实验记录实际实例、主机与端口、MySQL/Nacos 是否单实例、Dashboard 是否运行、入口直连或 Gateway、软件版本和时间。Flow Rule 默认为每个进程本地生效，不是集群配额。

| 拓扑 | Gateway | enrollment | training | user | MySQL | Nacos | 路径 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| A | 1 | 1 | 1，8082 | 1 | 1 | 1 | 单机直连或 Gateway；分别标记 |
| B | 1 | 1 | 3，8082/8085/8086 | 1 | 1 | 1 | Gateway + Nacos + LoadBalancer |
| C（可选） | 1 | 2 或 3 | 3 | 1 | 1 | 1 | Gateway + LoadBalancer |

以上是计划，不代表这些拓扑已经压测。实际服务器、数据库、客户端和共享资源需在每次结果旁记录，不能假设三实例容量等于三倍单实例容量。Gateway 阈值是每个 Gateway 实例的入口预算；服务规则是每个服务实例的预算。仅一个 Gateway 时，其局部额度才是该拓扑入口总预算。
