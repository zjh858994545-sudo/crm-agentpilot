param(
  [switch]$SkipDockerCheck
)

$ErrorActionPreference = "Stop"

Write-Host "CRM-AgentPilot production preflight"

$required = @(
  "AGENTPILOT_APP_PHASE",
  "AGENTPILOT_SECURITY_MODE",
  "AGENTPILOT_SEED_USERS_ENABLED",
  "AGENTPILOT_RATE_LIMIT_ENABLED",
  "SPRING_DATASOURCE_URL",
  "SPRING_DATASOURCE_USERNAME",
  "SPRING_DATASOURCE_PASSWORD",
  "AGENT_MODEL_PROVIDER",
  "OPENAI_COMPATIBLE_BASE_URL",
  "OPENAI_COMPATIBLE_API_KEY",
  "OPENAI_COMPATIBLE_CHAT_MODEL",
  "AGENT_EMBEDDING_PROVIDER",
  "OPENAI_COMPATIBLE_EMBEDDING_BASE_URL",
  "OPENAI_COMPATIBLE_EMBEDDING_API_KEY",
  "OPENAI_COMPATIBLE_EMBEDDING_MODEL",
  "OPENAI_COMPATIBLE_EMBEDDING_DIMENSIONS"
)

$failed = 0

function Get-EnvValue {
  param([string]$Name)
  $value = [Environment]::GetEnvironmentVariable($Name, "Process")
  if (-not $value) {
    $value = [Environment]::GetEnvironmentVariable($Name, "User")
  }
  if (-not $value) {
    $value = [Environment]::GetEnvironmentVariable($Name, "Machine")
  }
  return $value
}

function Assert-Env {
  param([string]$Name)
  $value = Get-EnvValue $Name
  if (-not $value -or $value.Trim().Length -eq 0) {
    Write-Host "[FAIL] missing $Name" -ForegroundColor Red
    return $false
  }
  Write-Host "[OK] $Name"
  return $true
}

foreach ($item in $required) {
  if (-not (Assert-Env $item)) {
    $failed++
  }
}

$appPhase = (Get-EnvValue "AGENTPILOT_APP_PHASE")
if ($appPhase -notin @("prod", "production", "commercial", "launch")) {
  Write-Host "[FAIL] AGENTPILOT_APP_PHASE must be prod/production/commercial/launch for production" -ForegroundColor Red
  $failed++
}

$securityMode = Get-EnvValue "AGENTPILOT_SECURITY_MODE"
if ($securityMode -ne "strict") {
  Write-Host "[FAIL] AGENTPILOT_SECURITY_MODE must be strict for production" -ForegroundColor Red
  $failed++
}

$seedUsers = Get-EnvValue "AGENTPILOT_SEED_USERS_ENABLED"
if ($seedUsers -ne "false") {
  Write-Host "[FAIL] AGENTPILOT_SEED_USERS_ENABLED must be false for production" -ForegroundColor Red
  $failed++
}

$rateLimitEnabled = Get-EnvValue "AGENTPILOT_RATE_LIMIT_ENABLED"
if ($rateLimitEnabled -ne "true") {
  Write-Host "[FAIL] AGENTPILOT_RATE_LIMIT_ENABLED must be true for production" -ForegroundColor Red
  $failed++
}

$jwtEnabled = Get-EnvValue "AGENTPILOT_JWT_ENABLED"
if ($jwtEnabled -eq "true") {
  $jwtIssuer = Get-EnvValue "AGENTPILOT_JWT_ISSUER_URI"
  $jwtTenants = Get-EnvValue "AGENTPILOT_JWT_ALLOWED_TENANTS"
  if (-not $jwtIssuer -or $jwtIssuer.Trim().Length -eq 0) {
    Write-Host "[FAIL] AGENTPILOT_JWT_ISSUER_URI is required when JWT SSO is enabled" -ForegroundColor Red
    $failed++
  }
  if (-not $jwtTenants -or $jwtTenants.Trim().Length -eq 0) {
    Write-Host "[FAIL] AGENTPILOT_JWT_ALLOWED_TENANTS is required when JWT SSO is enabled" -ForegroundColor Red
    $failed++
  }
}

$embeddingDimensions = Get-EnvValue "OPENAI_COMPATIBLE_EMBEDDING_DIMENSIONS"
if ($embeddingDimensions -ne "1024") {
  Write-Host "[WARN] OPENAI_COMPATIBLE_EMBEDDING_DIMENSIONS is $embeddingDimensions; expected 1024 for text-embedding-v4"
}

if (-not $SkipDockerCheck) {
  try {
    docker version | Out-Null
    Write-Host "[OK] Docker CLI can reach Docker Engine"
  } catch {
    Write-Host "[FAIL] Docker Engine is not reachable. Start Docker Desktop or use a server runtime." -ForegroundColor Red
    $failed++
  }
}

if ($failed -gt 0) {
  throw "$failed production preflight checks failed."
}

Write-Host "Production preflight passed."
