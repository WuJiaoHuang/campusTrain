param(
    [Parameter(Mandatory=$true)][ValidateSet('auth-login','learning-progress','community-like','community-unlike','oss-url')][string]$Resource,
    [string]$BaseUrl = 'http://192.168.10.102',
    [ValidateRange(1,100000)][int]$Qps = 1,
    [ValidateRange(1,10000)][int]$Threads = 2,
    [ValidateRange(1,86400)][int]$Duration = 10,
    [ValidateRange(0,3600)][int]$RampUp = 2,
    [long]$TrainingId = 199001,
    [long]$LessonId = 199001,
    [long]$QuestionId = 199000001,
    [long]$AnswerId = 199000001,
    [ValidateSet('QUESTION','ANSWER')][string]$TargetType = 'QUESTION',
    [ValidateRange(0,100000)][int]$ProgressSeconds = 10,
    [string]$JMeterPath = '',
    [switch]$RequireSuccess
)
$ErrorActionPreference = 'Stop'
$base = Split-Path -Parent $PSScriptRoot
$plan = Join-Path $base "jmeter/$Resource.jmx"
if (-not (Test-Path -LiteralPath $plan)) { throw "JMeter plan missing: $plan" }
if (-not $JMeterPath) {
    if ($env:JMETER_HOME) { $JMeterPath = Join-Path $env:JMETER_HOME 'bin/jmeter.bat' }
    elseif (Get-Command jmeter -ErrorAction SilentlyContinue) { $JMeterPath = (Get-Command jmeter).Source }
    else { $JMeterPath = 'D:/apache-jmeter-5.6.3/bin/jmeter.bat' }
}
if (-not (Test-Path -LiteralPath $JMeterPath)) { throw "JMeter CLI missing: $JMeterPath" }
if ($Resource -eq 'auth-login') {
    if (-not $env:BENCHMARK_USERNAME -or -not $env:BENCHMARK_PASSWORD) {
        throw 'Auth smoke needs BENCHMARK_USERNAME and BENCHMARK_PASSWORD.'
    }
} elseif (-not $env:BENCHMARK_TOKEN) {
    $env:BENCHMARK_TOKEN = & (Join-Path $PSScriptRoot 'get-token.ps1') -BaseUrl $BaseUrl
}
$targetId = if ($TargetType -eq 'ANSWER') { $AnswerId } else { $QuestionId }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$output = Join-Path $base "results/$Resource-$stamp"
New-Item -ItemType Directory -Force -Path $output | Out-Null
$jtl = Join-Path $output 'samples.jtl'
$metadata = [ordered]@{
    resource = $Resource; baseUrl = $BaseUrl; targetQps = $Qps; threads = $Threads
    durationSeconds = $Duration; rampUpSeconds = $RampUp; trainingId = $TrainingId
    lessonId = $LessonId; targetType = $TargetType; targetId = $targetId
    access = 'nginx-gateway'; timestamp = $stamp
}
$metadata | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'topology.json') -Encoding utf8
$arguments = @(
    '-n','-t',$plan,'-l',$jtl,
    "-JBASE_URL=$BaseUrl","-JTARGET_QPS=$Qps","-JTHREADS=$Threads",
    "-JDURATION=$Duration","-JRAMP_UP=$RampUp","-JTRAINING_ID=$TrainingId",
    "-JLESSON_ID=$LessonId","-JQUESTION_ID=$QuestionId","-JANSWER_ID=$AnswerId",
    "-JTARGET_TYPE=$TargetType","-JTARGET_ID=$targetId","-JPROGRESS_SECONDS=$ProgressSeconds",
    '-Jjmeter.save.saveservice.output_format=csv'
)
& $JMeterPath @arguments
if ($LASTEXITCODE -ne 0) { throw "JMeter failed (exit $LASTEXITCODE); see $output" }
python (Join-Path $base 'summarize.py') $jtl --output (Join-Path $output 'summary.json')
if ($LASTEXITCODE -ne 0) { throw "JTL summarization failed: $jtl" }
if ($RequireSuccess) {
    $summary = Get-Content -LiteralPath (Join-Path $output 'summary.json') -Raw | ConvertFrom-Json
    if ($summary.errorPercent -ne 0 -or $summary.http200 -ne $summary.samples) {
        throw "Smoke failed: samples=$($summary.samples), 200=$($summary.http200), errorPercent=$($summary.errorPercent). See $output"
    }
}
Write-Output "JTL and summary: $output"
