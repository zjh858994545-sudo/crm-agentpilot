param(
    [string]$EnvFile = ".env.production",
    [switch]$Build,
    [switch]$Pull,
    [switch]$NoHealthcheck
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $root $EnvFile }
$nginxDir = Join-Path $root "deploy\nginx"
$generatedDir = Join-Path $nginxDir "generated"
$templatePath = Join-Path $nginxDir "agentpilot.conf.template"
$generatedPath = Join-Path $generatedDir "agentpilot.conf"

function Import-DotEnv($path) {
    if (-not (Test-Path $path)) {
        throw "Environment file not found: $path. Copy .env.production.example to .env.production and fill secrets."
    }
    Get-Content $path | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }
        $idx = $line.IndexOf("=")
        if ($idx -le 0) {
            return
        }
        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
        Set-Item -Path "Env:$name" -Value $value
    }
}

function Require-Env($name) {
    $value = [Environment]::GetEnvironmentVariable($name, "Process")
    if ([string]::IsNullOrWhiteSpace($value) -or $value -like "<*secret*>" -or $value -eq "example.internal") {
        throw "Required environment variable $name is missing or still a placeholder."
    }
}

Import-DotEnv $envPath

@(
    "AGENTPILOT_DOMAIN",
    "POSTGRES_DB",
    "POSTGRES_USER",
    "POSTGRES_PASSWORD",
    "OPENAI_COMPATIBLE_BASE_URL",
    "OPENAI_COMPATIBLE_API_KEY",
    "OPENAI_COMPATIBLE_CHAT_MODEL",
    "OPENAI_COMPATIBLE_EMBEDDING_BASE_URL",
    "OPENAI_COMPATIBLE_EMBEDDING_API_KEY",
    "AGENTPILOT_CALLCENTER_WEBHOOK_SECRET"
) | ForEach-Object { Require-Env $_ }

New-Item -ItemType Directory -Force $generatedDir | Out-Null
$template = Get-Content $templatePath -Raw
$template = $template.Replace('${AGENTPILOT_DOMAIN}', $env:AGENTPILOT_DOMAIN)
Set-Content -Path $generatedPath -Value $template -Encoding UTF8

$composeArgs = @(
    "compose",
    "--env-file", $envPath,
    "-f", (Join-Path $root "docker-compose.yml"),
    "-f", (Join-Path $root "docker-compose.prod.yml")
)

if ($Pull) {
    & docker @composeArgs pull
    if ($LASTEXITCODE -ne 0) { throw "docker compose pull failed" }
}

$upArgs = $composeArgs + @("up", "-d")
if ($Build) {
    $upArgs += "--build"
}

& docker @upArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }

if (-not $NoHealthcheck) {
    $baseUrl = "https://$($env:AGENTPILOT_DOMAIN)"
    Write-Host "[WAIT] $baseUrl/actuator/health"
    for ($i = 0; $i -lt 60; $i++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/actuator/health" -TimeoutSec 5
            if ($response.StatusCode -eq 200) {
                Write-Host "[OK] production healthcheck passed"
                exit 0
            }
        } catch {
            Start-Sleep -Seconds 5
        }
    }
    throw "Production healthcheck did not pass: $baseUrl/actuator/health"
}

Write-Host "[OK] production stack started"
