param(
    [string]$BaseUrl = 'http://192.168.10.102',
    [string]$Username = $env:BENCHMARK_USERNAME,
    [string]$Password = $env:BENCHMARK_PASSWORD
)
$ErrorActionPreference = 'Stop'
if (-not $Username -or -not $Password) { throw 'Set BENCHMARK_USERNAME and BENCHMARK_PASSWORD in the environment.' }
$body = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
$response = Invoke-RestMethod -Method Post -Uri "$($BaseUrl.TrimEnd('/'))/auth/login" -ContentType 'application/json' -Body $body -TimeoutSec 20
if ($response.code -ne 200 -or -not $response.data.token) { throw 'Login succeeded without a usable JWT.' }
Write-Output $response.data.token
