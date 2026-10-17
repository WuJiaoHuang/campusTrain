param([string]$BaseUrl = 'http://localhost:8082', [long]$TrainingId = 90001, [string]$Token = '')
& "$PSScriptRoot/load-common.ps1" -BaseUrl $BaseUrl -TrainingId $TrainingId -Token $Token -Rounds 5 -RequestsPerRound 5 -RoundDelayMs 300
