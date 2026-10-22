# Stage14 JMeter

Run the SQL reset and `..\reset-redis.ps1` before each plan. The plans target a temporary isolated `enrollment-service` on port `18083`; its test headers create the same Stage7 user context that Gateway normally supplies. The production path still uses Gateway JWT validation.

```powershell
jmeter -n -t .\stage14-capacity-100.jmx -l ..\results\capacity-100.jtl -e -o ..\results\capacity-100-html -Jhost=localhost -Jport=18083 -Jthreads=1000 -JrampSeconds=10
jmeter -n -t .\stage14-duplicate-user.jmx -l ..\results\duplicate-user.jtl -e -o ..\results\duplicate-user-html -Jhost=localhost -Jport=18083 -Jthreads=100 -JrampSeconds=1
```

The `results` directory intentionally stores only real artifacts generated during an actual run. Never commit invented JTL or HTML output.
