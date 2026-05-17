param(
  [string]$BaseUrl = $(if ($env:AGENTPILOT_BASE_URL) { $env:AGENTPILOT_BASE_URL } else { "http://localhost:18080" }),
  [string]$Token = $(if ($env:AGENTPILOT_API_TOKEN) { $env:AGENTPILOT_API_TOKEN } else { "" })
)

$ErrorActionPreference = "Stop"

function Invoke-AgentPilot {
  param(
    [string]$Path,
    [string]$Method = "GET"
  )
  $headers = @{ "X-Trace-Id" = "ops-healthcheck-$(Get-Date -Format yyyyMMddHHmmss)" }
  if ($Token -and $Token.Trim().Length -gt 0) {
    $headers["X-AgentPilot-Token"] = $Token
  }
  Invoke-RestMethod -Method $Method -Uri "$BaseUrl$Path" -Headers $headers
}

Write-Host "CRM-AgentPilot operations healthcheck"
Write-Host "BaseUrl: $BaseUrl"

$checks = @(
  @{ name = "Health"; path = "/api/health" },
  @{ name = "Security"; path = "/api/security/status" },
  @{ name = "Model"; path = "/api/model/status" },
  @{ name = "Knowledge"; path = "/api/knowledge/status" },
  @{ name = "Events"; path = "/api/events/status" },
  @{ name = "Readiness"; path = "/api/operations/readiness" },
  @{ name = "Retention"; path = "/api/operations/retention" }
)

$failed = 0
foreach ($check in $checks) {
  try {
    $result = Invoke-AgentPilot -Path $check.path
    $code = if ($result.code) { $result.code } else { "OK" }
    Write-Host "[OK] $($check.name) $($check.path) code=$code"
  } catch {
    $failed++
    Write-Host "[FAIL] $($check.name) $($check.path) $($_.Exception.Message)" -ForegroundColor Red
  }
}

if ($failed -gt 0) {
  throw "$failed operations checks failed."
}

Write-Host "Operations healthcheck passed."
