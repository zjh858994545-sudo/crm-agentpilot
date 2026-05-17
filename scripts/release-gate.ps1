param(
    [switch]$SkipBackendTests,
    [switch]$SkipFrontendBuild,
    [switch]$SkipPreflight,
    [switch]$SkipRuntimeHealthcheck,
    [switch]$SkipDockerCheck,
    [string]$MavenRepo = $(if ($env:MAVEN_REPO_LOCAL) { $env:MAVEN_REPO_LOCAL } else { "F:\后端开发新项目\DevCache\m2" }),
    [string]$BaseUrl = $(if ($env:AGENTPILOT_BASE_URL) { $env:AGENTPILOT_BASE_URL } else { "http://localhost:18080" }),
    [string]$Token = $(if ($env:AGENTPILOT_API_TOKEN) { $env:AGENTPILOT_API_TOKEN } else { "" })
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$startedAt = Get-Date
$summary = New-Object System.Collections.Generic.List[string]

function Invoke-Step {
    param(
        [string]$Name,
        [scriptblock]$Action
    )
    $stepStart = Get-Date
    Write-Host ""
    Write-Host "[GATE] $Name" -ForegroundColor Cyan
    & $Action
    $elapsed = [Math]::Round(((Get-Date) - $stepStart).TotalSeconds, 1)
    $summary.Add("${Name}: ${elapsed}s") | Out-Null
    Write-Host "[OK] $Name (${elapsed}s)" -ForegroundColor Green
}

Write-Host "CRM-AgentPilot release gate"
Write-Host "Workspace: $root"

if (-not $SkipBackendTests) {
    Invoke-Step "Backend tests" {
        Push-Location (Join-Path $root "backend")
        try {
            mvn "-Dmaven.repo.local=$MavenRepo" test
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipFrontendBuild) {
    Invoke-Step "Frontend production build" {
        Push-Location (Join-Path $root "frontend")
        try {
            npm run build
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipPreflight) {
    Invoke-Step "Static production preflight" {
        $args = @()
        if ($SkipDockerCheck) {
            $args += "-SkipDockerCheck"
        }
        & (Join-Path $PSScriptRoot "preflight-production.ps1") @args
    }
}

if (-not $SkipRuntimeHealthcheck) {
    Invoke-Step "Runtime operations healthcheck" {
        & (Join-Path $PSScriptRoot "ops-healthcheck.ps1") -BaseUrl $BaseUrl -Token $Token
    }
}

$total = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
Write-Host ""
Write-Host "Release gate passed in ${total}s." -ForegroundColor Green
Write-Host "Summary:"
$summary | ForEach-Object { Write-Host "  - $_" }
