param(
    [string]$BaseUrl = 'http://192.168.10.102',
    [int]$Qps = 2, [int]$Threads = 2, [int]$Duration = 10, [int]$RampUp = 2,
    [long]$TrainingId = 199001, [long]$LessonId = 199001,
    [long]$QuestionId = 199000001, [long]$AnswerId = 199000001,
    [ValidateSet('QUESTION','ANSWER')][string]$TargetType = 'QUESTION',
    [int]$ProgressSeconds = 10, [string]$JMeterPath = '', [switch]$RequireSuccess
)
$parameters = @{
    Resource='auth-login'; BaseUrl=$BaseUrl; Qps=$Qps; Threads=$Threads; Duration=$Duration; RampUp=$RampUp
    TrainingId=$TrainingId; LessonId=$LessonId; QuestionId=$QuestionId; AnswerId=$AnswerId
    TargetType=$TargetType; ProgressSeconds=$ProgressSeconds; JMeterPath=$JMeterPath; RequireSuccess=$RequireSuccess
}
& (Join-Path $PSScriptRoot 'run-common.ps1') @parameters
