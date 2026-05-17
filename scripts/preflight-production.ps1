param(
  [switch]$SkipDockerCheck
)

$ErrorActionPreference = "Stop"

Write-Host "CRM-AgentPilot production preflight"

$required = @(
  "AGENTPILOT_SECURITY_MODE",
  "AGENTPILOT_SEED_USERS_ENABLED",
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

function Assert-Env {
  param([string]$Name)
  $value = [Environment]::GetEnvironmentVariable($Name, "Process")
  if (-not $value) {
    $value = [Environment]::GetEnvironmentVariable($Name, "User")
  }
  if (-not $value) {
    $value = [Environment]::GetEnvironmentVariable($Name, "Machine")
  }
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

$securityMode = [Environment]::GetEnvironmentVariable("AGENTPILOT_SECURITY_MODE", "User")
if ($securityMode -ne "strict") {
  Write-Host "[FAIL] AGENTPILOT_SECURITY_MODE must be strict for production" -ForegroundColor Red
  $failed++
}

$seedUsers = [Environment]::GetEnvironmentVariable("AGENTPILOT_SEED_USERS_ENABLED", "User")
if ($seedUsers -ne "false") {
  Write-Host "[FAIL] AGENTPILOT_SEED_USERS_ENABLED must be false for production" -ForegroundColor Red
  $failed++
}

$embeddingDimensions = [Environment]::GetEnvironmentVariable("OPENAI_COMPATIBLE_EMBEDDING_DIMENSIONS", "User")
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
