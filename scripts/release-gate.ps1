param(
    [switch]$SkipBackendTests,
    [switch]$SkipFrontendBuild,
    [switch]$SkipFrontendBundleBudget,
    [switch]$SkipPreflight,
    [switch]$SkipRuntimeHealthcheck,
    [switch]$SkipAdminHealthchecks,
    [switch]$SkipDockerCheck,
    [string]$EnvFile = "",
    [string]$MavenRepo = $(if ($env:MAVEN_REPO_LOCAL) { $env:MAVEN_REPO_LOCAL } else { "F:\DockerData\AgentPilotCache\m2" }),
    [string]$BaseUrl = $(if ($env:AGENTPILOT_BASE_URL) { $env:AGENTPILOT_BASE_URL } else { "http://localhost:18080" }),
    [string]$Token = $(if ($env:AGENTPILOT_API_TOKEN) { $env:AGENTPILOT_API_TOKEN } else { "" }),
    [string]$BearerToken = $(if ($env:AGENTPILOT_BEARER_TOKEN) { $env:AGENTPILOT_BEARER_TOKEN } elseif ($env:AGENTPILOT_JWT_TOKEN) { $env:AGENTPILOT_JWT_TOKEN } else { "" })
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$startedAt = Get-Date
$summary = New-Object System.Collections.Generic.List[string]

function Invoke-Native {
    param(
        [string]$FilePath,
        [string[]]$Arguments = @()
    )
    & $FilePath @Arguments
    $exitCode = $LASTEXITCODE
    if ($null -ne $exitCode -and $exitCode -ne 0) {
        throw "$FilePath exited with code $exitCode"
    }
}

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
            New-Item -ItemType Directory -Force -Path $MavenRepo | Out-Null
            Invoke-Native "mvn" @("-Dmaven.repo.local=$MavenRepo", "test")
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipFrontendBuild) {
    Invoke-Step "Frontend production build" {
        Push-Location (Join-Path $root "frontend")
        try {
            Invoke-Native "npm" @("run", "build")
        } finally {
            Pop-Location
        }
    }
}

if (-not $SkipFrontendBuild -and -not $SkipFrontendBundleBudget) {
    Invoke-Step "Frontend bundle budget" {
        & (Join-Path $PSScriptRoot "check-frontend-bundle.ps1")
    }
}

if (-not $SkipPreflight) {
    Invoke-Step "Static production preflight" {
        $script = Join-Path $PSScriptRoot "preflight-production.ps1"
        if ($EnvFile -and $EnvFile.Trim().Length -gt 0 -and $SkipDockerCheck) {
            & $script -EnvFile $EnvFile -SkipDockerCheck
        } elseif ($EnvFile -and $EnvFile.Trim().Length -gt 0) {
            & $script -EnvFile $EnvFile
        } elseif ($SkipDockerCheck) {
            & $script -SkipDockerCheck
        } else {
            & $script
        }
    }
}

if (-not $SkipRuntimeHealthcheck) {
    Invoke-Step "Runtime operations healthcheck" {
        $args = @("-BaseUrl", $BaseUrl, "-Token", $Token, "-BearerToken", $BearerToken)
        if ($SkipAdminHealthchecks) {
            $args += "-SkipAdminChecks"
        }
        & (Join-Path $PSScriptRoot "ops-healthcheck.ps1") @args
    }
}

$total = [Math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
Write-Host ""
Write-Host "Release gate passed in ${total}s." -ForegroundColor Green
Write-Host "Summary:"
$summary | ForEach-Object { Write-Host "  - $_" }
