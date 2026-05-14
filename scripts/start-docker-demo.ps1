param(
    [int]$BackendPort = 0,
    [int]$FrontendPort = 0,
    [switch]$NoSmoke,
    [switch]$OpenBrowser,
    [switch]$NoFallback
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot
$logsDir = Join-Path $root ".demo-logs"
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

function Get-DockerCommand {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        return $docker.Source
    }
    $knownDocker = "F:\DockerDesktop\resources\bin\docker.exe"
    if (Test-Path $knownDocker) {
        return $knownDocker
    }
    throw "Docker CLI was not found. Please start Docker Desktop or add docker.exe to PATH."
}

function Import-UserEnvironment($name) {
    $currentValue = [Environment]::GetEnvironmentVariable($name, "Process")
    if (-not [string]::IsNullOrWhiteSpace($currentValue)) {
        return
    }
    $userValue = [Environment]::GetEnvironmentVariable($name, "User")
    if (-not [string]::IsNullOrWhiteSpace($userValue)) {
        Set-Item -Path "Env:$name" -Value $userValue
    }
}

function Wait-Backend($baseUrl) {
    for ($i = 0; $i -lt 90; $i++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/api/health" -TimeoutSec 2
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    throw "Backend did not become healthy at $baseUrl/api/health. Run 'docker logs agentpilot-backend' for details."
}

function Test-PortInUse($port) {
    $connection = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    return $null -ne $connection
}

function Get-AvailablePort($preferred, $fallbackStart) {
    if (-not (Test-PortInUse $preferred)) {
        return $preferred
    }
    for ($port = $fallbackStart; $port -lt ($fallbackStart + 100); $port++) {
        if (-not (Test-PortInUse $port)) {
            return $port
        }
    }
    throw "No available port found near $fallbackStart."
}

function Show-DockerPullHelp {
    Write-Host ""
    Write-Host "Docker could not pull build images from Docker Hub."
    Write-Host "If you are using a local proxy, configure Docker Desktop:"
    Write-Host "Settings -> Resources -> Proxies -> Manual proxy"
    Write-Host "HTTP proxy:  http://127.0.0.1:7890"
    Write-Host "HTTPS proxy: http://127.0.0.1:7890"
    Write-Host "Then Apply & Restart Docker Desktop and run this script again."
    Write-Host ""
}

function Set-DockerBackendJavaProxyOptions {
    if (-not [string]::IsNullOrWhiteSpace($env:BACKEND_JAVA_TOOL_OPTIONS)) {
        return
    }
    $proxyUri = $env:HTTPS_PROXY
    if ([string]::IsNullOrWhiteSpace($proxyUri)) {
        $proxyUri = $env:HTTP_PROXY
    }
    if ([string]::IsNullOrWhiteSpace($proxyUri) -and (Test-PortInUse 7890)) {
        $proxyUri = "http://host.docker.internal:7890"
    }
    if ([string]::IsNullOrWhiteSpace($proxyUri)) {
        return
    }
    $uri = [Uri]$proxyUri
    $proxyHost = $uri.Host
    if ($proxyHost -eq "127.0.0.1" -or $proxyHost -eq "localhost") {
        $proxyHost = "host.docker.internal"
    }
    $env:BACKEND_JAVA_TOOL_OPTIONS = "-Dhttp.proxyHost=$proxyHost -Dhttp.proxyPort=$($uri.Port) -Dhttps.proxyHost=$proxyHost -Dhttps.proxyPort=$($uri.Port) -Dhttp.nonProxyHosts=localhost|127.*|[::1]|postgres|redis|kafka"
    Write-Host "[CONFIG] Docker backend Java proxy ${proxyHost}:$($uri.Port)"
}

@(
    "AGENT_MODEL_PROVIDER",
    "OPENAI_COMPATIBLE_BASE_URL",
    "OPENAI_COMPATIBLE_API_KEY",
    "OPENAI_COMPATIBLE_CHAT_MODEL",
    "OPENAI_COMPATIBLE_TEMPERATURE",
    "AGENT_EVENTS_KAFKA_ENABLED",
    "HTTP_PROXY",
    "HTTPS_PROXY",
    "NO_PROXY"
) | ForEach-Object { Import-UserEnvironment $_ }

Set-DockerBackendJavaProxyOptions

if ($BackendPort -le 0) {
    $BackendPort = Get-AvailablePort 18080 18081
}
if ($FrontendPort -le 0) {
    $FrontendPort = Get-AvailablePort 15173 15174
}

$env:BACKEND_PORT = "$BackendPort"
$env:FRONTEND_PORT = "$FrontendPort"

if ([string]::IsNullOrWhiteSpace($env:AGENT_MODEL_PROVIDER)) {
    $env:AGENT_MODEL_PROVIDER = "mock"
}

$backendUrl = "http://localhost:$BackendPort"
$frontendUrl = "http://localhost:$FrontendPort"
Set-Content -Path (Join-Path $logsDir "backend.url") -Value $backendUrl -Encoding ASCII
Set-Content -Path (Join-Path $logsDir "frontend.url") -Value $frontendUrl -Encoding ASCII

$docker = Get-DockerCommand
Write-Host "[RUN] stop old local demo processes if any"
& (Join-Path $PSScriptRoot "stop-full-demo.ps1")

$composeArgs = @(
    "compose",
    "-f", (Join-Path $root "docker-compose.yml"),
    "-f", (Join-Path $root "docker-compose.full.yml"),
    "up", "-d", "--build"
)

Write-Host "[RUN] docker compose full stack"
& $docker @composeArgs
if ($LASTEXITCODE -ne 0) {
    Show-DockerPullHelp
    if ($NoFallback) {
        throw "Docker Compose full stack failed. Fix Docker Hub connectivity or rerun without -NoFallback."
    }
    Write-Host "[FALLBACK] Starting demo with Docker infrastructure + local backend/frontend."
    Write-Host "[FALLBACK] This keeps one-command startup working while Docker Hub is unavailable."
    & (Join-Path $PSScriptRoot "start-full-demo.ps1")
    exit $LASTEXITCODE
}

Write-Host "[WAIT] backend health"
Wait-Backend $backendUrl

if (-not $NoSmoke) {
    Write-Host "[RUN] smoke check"
    & (Join-Path $PSScriptRoot "smoke-demo.ps1") -BaseUrl $backendUrl
}

Write-Host ""
Write-Host "CRM-AgentPilot is ready."
Write-Host "Frontend: $frontendUrl"
Write-Host "Backend:  $backendUrl/api/health"
Write-Host "Swagger:  $backendUrl/swagger-ui.html"
Write-Host ""
Write-Host "Model provider: $env:AGENT_MODEL_PROVIDER"
Write-Host "Model key configured: $(-not [string]::IsNullOrWhiteSpace($env:OPENAI_COMPATIBLE_API_KEY))"
Write-Host ""
Write-Host "Stop:"
Write-Host ".\scripts\stop-docker-demo.ps1"
Write-Host ""
Write-Host "Reset database:"
Write-Host ".\scripts\stop-docker-demo.ps1 -ResetData"

if ($OpenBrowser) {
    Start-Process $frontendUrl
}
