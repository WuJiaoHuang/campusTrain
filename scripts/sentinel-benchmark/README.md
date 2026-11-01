# Sentinel 新资源压测脚本（Stage A）

本目录沿用 Stage10 的 JMeter CLI、ConstantThroughputTimer、CSV JTL 和全量样本 nearest-rank P90/P95/P99。Stage A 仅运行小流量 smoke；不据此确定容量或 Sentinel 阈值。

## 准备

- Windows PowerShell、JMeter 5.6.3 CLI、Python 3。JMeter 默认查找 `D:/apache-jmeter-5.6.3/bin/jmeter.bat`，也可传 `-JMeterPath` 或设置 `JMETER_HOME`。
- 数据准备脚本需要 `pymysql`、`bcrypt`，可运行 `python -m pip install pymysql bcrypt`。MySQL 地址及账号可由 `MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` 覆盖。
- 设置环境变量 `BENCHMARK_USERNAME`、`BENCHMARK_PASSWORD`，不要保存到仓库。若需手动指定 JWT，设置 `BENCHMARK_TOKEN`；默认由 `get-token.ps1` 登录获取。直接调用 JMeter 时也支持 `-JUSERNAME`、`-JPASSWORD`、`-JTOKEN`，但命令行参数可能出现在进程列表，优先使用环境变量。
- 首次准备专用数据：`python scripts/sentinel-benchmark/data/prepare_data.py`。随后运行 `python scripts/sentinel-benchmark/data/capture_baseline.py` 记录基线。只查询专用 ID 的 SQL 见 `data/baseline.sql`。

## 运行

在仓库根目录执行，例如：

```powershell
$env:BENCHMARK_USERNAME = 'benchmark_sentinel_student'
$env:BENCHMARK_PASSWORD = '<仅在本机输入测试密码>'
& scripts/sentinel-benchmark/run/run-auth.ps1 -Qps 2 -Threads 2 -Duration 10 -RampUp 2 -RequireSuccess
& scripts/sentinel-benchmark/run/run-learning.ps1 -Qps 2 -Threads 1 -Duration 10 -RampUp 1 -LessonId 199001 -RequireSuccess
& scripts/sentinel-benchmark/run/run-community-like.ps1 -Qps 1 -Threads 1 -Duration 5 -RampUp 1 -TargetType QUESTION -RequireSuccess
& scripts/sentinel-benchmark/run/run-community-unlike.ps1 -Qps 1 -Threads 1 -Duration 5 -RampUp 1 -TargetType QUESTION -RequireSuccess
& scripts/sentinel-benchmark/run/run-oss.ps1 -Qps 1 -Threads 1 -Duration 10 -RampUp 1 -LessonId 199001 -RequireSuccess
```

五个脚本均接受 `-BaseUrl`、`-Qps`、`-Threads`、`-Duration`、`-RampUp`、`-TrainingId`、`-LessonId`、`-QuestionId`、`-AnswerId`、`-TargetType`、`-ProgressSeconds`、`-JMeterPath`。`-RequireSuccess` 在 smoke 中检查每个样本 HTTP 200 且断言通过；正式压测不要启用，因为 429/503 是有意义的观测值。OSS 文件包含两个独立 Thread Group；设置 `-Qps 1` 时两个资源各自目标 1 QPS，合计目标约 2 QPS。JMeter 短时调度会受启动、预热及后端响应时间影响，实际 QPS 以 JTL 计算。

原生 JMeter CLI 示例：

```powershell
& 'D:/apache-jmeter-5.6.3/bin/jmeter.bat' -n -t scripts/sentinel-benchmark/jmeter/auth-login.jmx -l scripts/sentinel-benchmark/results/manual-auth.jtl -JBASE_URL=http://192.168.10.102 -JTARGET_QPS=2 -JTHREADS=2 -JDURATION=10 -JRAMP_UP=2
```

环境变量可提供登录凭证或 JWT。`BASE_URL` 默认 `http://192.168.10.102`；五个 JMX 均支持 `TRAINING_ID`、`LESSON_ID`、`QUESTION_ID`、`ANSWER_ID`、`TARGET_QPS`、`THREADS`、`DURATION`、`RAMP_UP`，并在适用的采样器使用对应值。JMX 由 `jmeter/generate_plans.py` 生成，修改模板后重新运行该脚本。

每轮生成 `results/<资源>-<时间>/samples.jtl`、`summary.json`、`topology.json`。结果目录被 Git 忽略；`topology.json` 仅记录运行参数，不保存凭证。重新汇总：

```powershell
python scripts/sentinel-benchmark/summarize.py scripts/sentinel-benchmark/results/<资源>-<时间>/samples.jtl
```

汇总包括样本数、实际时间窗与 QPS、平均、P90/P95/P99、最大耗时、错误率、200/400/429/500/503、404/405、超时、连接错误及按 sampler 的明细。百分位按 Stage10 的全量 JTL nearest-rank 计算。JMeter 可以另用 `-e -o <目录>` 生成 HTML 报告，但本阶段不提交大型报告或 JTL。

详见 [Stage A 验收记录](../../docs/refactor/sentinel-benchmark.md)。
