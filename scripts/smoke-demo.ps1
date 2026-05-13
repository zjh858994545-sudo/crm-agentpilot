param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$RunDemo
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Assert-True($condition, $message) {
    if (-not $condition) {
        throw $message
    }
}

function Invoke-AgentPilotWeb($method, $path, $headers = $null) {
    $uri = "$BaseUrl$path"
    return Invoke-WebRequest -Method $method -Uri $uri -Headers $headers -UseBasicParsing
}

function Get-ResponseText($response) {
    if ($response.RawContentStream) {
        $response.RawContentStream.Position = 0
        $reader = New-Object System.IO.StreamReader($response.RawContentStream, [System.Text.Encoding]::UTF8)
        return $reader.ReadToEnd()
    }
    if ($response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($response.Content)
    }
    return [string]$response.Content
}

Write-Host "CRM-AgentPilot smoke check"
Write-Host "BaseUrl: $BaseUrl"

$traceId = "interview-smoke-" + (Get-Date -Format "yyyyMMddHHmmss")
$healthResponse = Invoke-AgentPilotWeb Get "/api/health" @{ "X-Trace-Id" = $traceId }
$health = Get-ResponseText $healthResponse | ConvertFrom-Json
Assert-True ($health.data.status -eq "UP") "Health check failed"
Assert-True ($healthResponse.Headers["X-Trace-Id"] -eq $traceId) "Trace header was not echoed"
Write-Host "[OK] /api/health, X-Trace-Id=$traceId"

$model = Get-ResponseText (Invoke-AgentPilotWeb Get "/api/model/status") | ConvertFrom-Json
Assert-True ($null -ne $model.data.mode) "Model status failed"
Write-Host "[OK] /api/model/status mode=$($model.data.mode), model=$($model.data.model)"

$events = Get-ResponseText (Invoke-AgentPilotWeb Get "/api/events/status") | ConvertFrom-Json
Assert-True ($null -ne $events.data.mode) "Events status failed"
Write-Host "[OK] /api/events/status mode=$($events.data.mode)"

$actuator = Get-ResponseText (Invoke-AgentPilotWeb Get "/actuator/health") | ConvertFrom-Json
Assert-True ($actuator.status -eq "UP") "Actuator health failed"
Write-Host "[OK] /actuator/health"

$apiDocs = Invoke-AgentPilotWeb Get "/v3/api-docs"
Assert-True ((Get-ResponseText $apiDocs).Contains("CRM-AgentPilot API")) "OpenAPI docs failed"
Write-Host "[OK] /v3/api-docs"

if ($RunDemo) {
    Write-Host "[RUN] scripts/demo-api.ps1"
    $env:AGENTPILOT_BASE_URL = $BaseUrl
    & "$PSScriptRoot\demo-api.ps1"
}

Write-Host "Smoke check passed."
