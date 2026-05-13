$ErrorActionPreference = "Stop"

$baseUrl = $env:AGENTPILOT_BASE_URL
if ([string]::IsNullOrWhiteSpace($baseUrl)) {
    $baseUrl = "http://localhost:8080"
}

$response = Invoke-RestMethod -Method Post -Uri "$baseUrl/api/evaluation/run"
$response.data | ConvertTo-Json -Depth 8
