param(
  [string]$BaseUrl = $(if ($env:AGENTPILOT_BASE_URL) { $env:AGENTPILOT_BASE_URL } else { "http://localhost:18080" }),
  [string]$Token = $(if ($env:AGENTPILOT_API_TOKEN) { $env:AGENTPILOT_API_TOKEN } else { "" }),
  [switch]$SkipAdminChecks
)

$ErrorActionPreference = "Stop"

function New-TraceId {
  param([string]$Name)
  $safeName = ($Name -replace "[^a-zA-Z0-9]", "-").ToLowerInvariant()
  return "ops-healthcheck-$safeName-$(Get-Date -Format yyyyMMddHHmmssfff)"
}

function Invoke-AgentPilot {
  param(
    [string]$Name,
    [string]$Path,
    [string]$Method = "GET",
    [object]$Body = $null
  )

  $traceId = New-TraceId $Name
  $headers = @{ "X-Trace-Id" = $traceId }
  if ($Token -and $Token.Trim().Length -gt 0) {
    $headers["X-AgentPilot-Token"] = $Token
  }

  $invokeArgs = @{
    Method = $Method
    Uri = "$BaseUrl$Path"
    Headers = $headers
    UseBasicParsing = $true
  }
  if ($null -ne $Body) {
    $invokeArgs["ContentType"] = "application/json; charset=utf-8"
    $invokeArgs["Body"] = ($Body | ConvertTo-Json -Depth 10)
  }

  $response = Invoke-WebRequest @invokeArgs
  $responseTraceId = $response.Headers["X-Trace-Id"]
  if ($responseTraceId -is [array]) {
    $responseTraceId = $responseTraceId[0]
  }

  $parsedBody = $null
  if ($response.Content -and $response.Content.Trim().Length -gt 0) {
    $parsedBody = $response.Content | ConvertFrom-Json
  }

  return [pscustomobject]@{
    Body = $parsedBody
    StatusCode = $response.StatusCode
    RequestTraceId = $traceId
    ResponseTraceId = $responseTraceId
  }
}

function Assert-OperationalResult {
  param(
    [string]$Name,
    [object]$Response
  )

  if ($null -eq $Response -or $Response.StatusCode -lt 200 -or $Response.StatusCode -ge 300) {
    throw "$Name returned HTTP $($Response.StatusCode)."
  }

  if (-not $Response.ResponseTraceId) {
    throw "$Name did not return X-Trace-Id."
  }
  if ($Response.ResponseTraceId -ne $Response.RequestTraceId) {
    throw "$Name returned mismatched X-Trace-Id: expected $($Response.RequestTraceId), got $($Response.ResponseTraceId)."
  }

  $result = $Response.Body
  if ($null -eq $result -or ($result.PSObject.Properties.Name -contains "success" -and -not $result.success)) {
    throw "$Name returned an unsuccessful API response."
  }

  if ($Name -eq "Auth" -and (-not $result.data.userId -or -not $result.data.tenantId)) {
    throw "Authenticated profile is missing userId or tenantId."
  }

  if ($Name -eq "Security" -and $result.data.strictWithoutToken) {
    throw "Security is in strict mode without an access token or RBAC user."
  }

  if ($Name -eq "Security" -and $result.data.rateLimit.enabled -and $result.data.rateLimit.backend -eq "memory") {
    Write-Host "[WARN] Rate limiting is using memory backend; use redis for multi-instance production." -ForegroundColor Yellow
  }

  if ($Name -eq "Events" -and $result.data.outboxDeadLetters -gt 0) {
    throw "Outbox has $($result.data.outboxDeadLetters) dead-letter events."
  }

  if ($Name -eq "Readiness") {
    $overall = $result.data.overallStatus
    if ($overall -eq "BLOCKED") {
      throw "Launch readiness is BLOCKED: pass=$($result.data.passCount), warn=$($result.data.warnCount), fail=$($result.data.failCount)."
    }
    if ($overall -eq "WARN") {
      Write-Host "[WARN] Launch readiness has warnings: pass=$($result.data.passCount), warn=$($result.data.warnCount), fail=$($result.data.failCount)" -ForegroundColor Yellow
    }
  }

  if ($Name -eq "Tenants" -and ($null -eq $result.data -or $result.data.Count -eq 0)) {
    throw "Tenant registry is empty."
  }
}

Write-Host "CRM-AgentPilot operations healthcheck"
Write-Host "BaseUrl: $BaseUrl"

$checks = @(
  @{ name = "Health"; path = "/api/health" },
  @{ name = "Auth"; path = "/api/auth/me" },
  @{ name = "Security"; path = "/api/security/status" },
  @{ name = "Dashboard"; path = "/api/dashboard/metrics" },
  @{ name = "Model"; path = "/api/model/status" },
  @{ name = "Knowledge"; path = "/api/knowledge/status" },
  @{ name = "Events"; path = "/api/events/status" },
  @{ name = "Readiness"; path = "/api/operations/readiness" },
  @{ name = "Retention"; path = "/api/operations/retention" }
)

if (-not $SkipAdminChecks) {
  $checks += @{ name = "Tenants"; path = "/api/tenants" }
}

$failed = 0
foreach ($check in $checks) {
  try {
    $response = Invoke-AgentPilot -Name $check.name -Path $check.path
    Assert-OperationalResult -Name $check.name -Response $response
    $code = if ($response.Body.code) { $response.Body.code } else { "OK" }
    Write-Host "[OK] $($check.name) $($check.path) code=$code trace=$($response.ResponseTraceId)"
  } catch {
    $failed++
    Write-Host "[FAIL] $($check.name) $($check.path) $($_.Exception.Message)" -ForegroundColor Red
  }
}

if ($failed -gt 0) {
  throw "$failed operations checks failed."
}

Write-Host "Operations healthcheck passed."
