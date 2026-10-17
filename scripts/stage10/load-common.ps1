param(
    [string]$BaseUrl = 'http://localhost:8082',
    [long]$TrainingId = 90001,
    [string]$Token = '',
    [int]$Rounds = 1,
    [int]$RequestsPerRound = 20,
    [int]$RoundDelayMs = 1000
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(30)
if ($Token) { $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
try {
    for ($round = 1; $round -le $Rounds; $round++) {
        $tasks = @()
        for ($i = 0; $i -lt $RequestsPerRound; $i++) {
            $tasks += $client.GetAsync("$($BaseUrl.TrimEnd('/'))/trainings/$TrainingId")
        }
        foreach ($task in $tasks) {
            try {
                $response = $task.GetAwaiter().GetResult()
                $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                Write-Output "round=$round status=$([int]$response.StatusCode) body=$body"
                $response.Dispose()
            } catch { Write-Warning "round=$round request failed: $_" }
        }
        if ($round -lt $Rounds) { Start-Sleep -Milliseconds $RoundDelayMs }
    }
} finally { $client.Dispose() }
