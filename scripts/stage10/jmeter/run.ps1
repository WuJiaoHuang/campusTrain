param(
    [Parameter(Mandatory=$true)][ValidateSet('training-detail','training-enrollment-check','enrollment-create','gateway-enrollment')][string]$Resource,
    [Parameter(Mandatory=$true)][string]$Topology,
    [Parameter(Mandatory=$true)][ValidateSet('direct','gateway')][string]$Access,
    [Parameter(Mandatory=$true)][int]$GatewayInstances,
    [Parameter(Mandatory=$true)][int]$EnrollmentInstances,
    [Parameter(Mandatory=$true)][int]$TrainingInstances,
    [Parameter(Mandatory=$true)][int]$UserInstances,
    [Parameter(Mandatory=$true)][int]$MysqlInstances,
    [Parameter(Mandatory=$true)][int]$NacosInstances,
    [Parameter(Mandatory=$true)][bool]$DashboardRunning,
    [string]$HostName = 'localhost', [int]$Port = 8082, [long]$TrainingId = 90001,
    [int]$Threads = 10, [int]$Duration = 60, [int]$Qps = 5,
    [string]$Token = '', [string]$UserId = '', [string]$JMeter = 'jmeter',
    [string]$TrainingCheckUrl = 'http://localhost:8082', [switch]$ConfirmDraft
)
$ErrorActionPreference = 'Stop'
if ($Resource -in @('enrollment-create','gateway-enrollment') -and (-not $UserId -or ($Resource -eq 'gateway-enrollment' -and -not $Token))) {
    throw 'Provide UserId and, for Gateway, Token. Use an unregistered student and a draft training to avoid writes.'
}
if ($Resource -eq 'training-enrollment-check' -and $Access -eq 'gateway') { throw 'The internal check is not exposed by Gateway.' }
if ($Resource -eq 'gateway-enrollment' -and $Access -ne 'gateway') { throw 'gateway-enrollment requires gateway access.' }
if ($Resource -in @('enrollment-create','gateway-enrollment')) {
    if (-not $ConfirmDraft) { throw 'ConfirmDraft is required for enrollment tests.' }
    $training = Invoke-RestMethod "$($TrainingCheckUrl.TrimEnd('/'))/internal/trainings/$TrainingId/enrollment-check"
    if ($training.code -ne 200 -or $training.data.status -ne 0 -or $training.data.trainingId -ne $TrainingId) {
        throw 'Preflight failed: the dedicated training must exist and remain in draft status.'
    }
}
$path = switch ($Resource) {
    'training-detail' { "/trainings/$TrainingId" }
    'training-enrollment-check' { "/internal/trainings/$TrainingId/enrollment-check" }
    default { '/enrollments' }
}
$post = $Resource -in @('enrollment-create','gateway-enrollment')
$method = if ($post) { 'POST' } else { 'GET' }
$status = if ($post) { 400 } else { 200 }
$marker = if ($post) { '当前培训不在报名状态' } else { 'code.{0,3}200' }
$out = Join-Path $PSScriptRoot 'results'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$prefix = Join-Path $out "$Topology-$Resource-$stamp"
[ordered]@{ timestamp=$stamp; topology=$Topology; access=$Access; host=$HostName; port=$Port; path=$path;
    gatewayInstances=$GatewayInstances; enrollmentInstances=$EnrollmentInstances; trainingInstances=$TrainingInstances;
    userInstances=$UserInstances; mysqlInstances=$MysqlInstances; nacosInstances=$NacosInstances;
    dashboardRunning=$DashboardRunning; threads=$Threads; durationSeconds=$Duration; targetQps=$Qps;
    trainingId=$TrainingId; expectedStatus=$status } | ConvertTo-Json | Set-Content -Encoding utf8 "$prefix-topology.json"
& $JMeter -n -t (Join-Path $PSScriptRoot 'stage10.jmx') -l "$prefix.jtl" -e -o "$prefix-report" "-Jresource=$Resource" "-Jhost=$HostName" "-Jport=$Port" "-Jpath=$path" "-Jmethod=$method" "-JtrainingId=$TrainingId" "-JexpectedStatus=$status" "-JexpectedBody=$marker" "-Jthreads=$Threads" "-Jduration=$Duration" "-Jqps=$Qps" "-Jtoken=$Token" "-JuserId=$UserId"
if ($LASTEXITCODE -ne 0) { throw "JMeter exited with code $LASTEXITCODE" }
Write-Output "JTL: $prefix.jtl; HTML: $prefix-report; topology: $Topology"
