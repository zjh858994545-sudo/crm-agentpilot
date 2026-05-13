$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$logsDir = Join-Path $root ".demo-logs"
$runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
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
    throw "Docker CLI was not found. Start Docker Desktop or add docker.exe to PATH."
}

function Quote-ForPowerShell($value) {
    return "'" + ($value -replace "'", "''") + "'"
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

function Start-DemoProcess($name, $workDir, $command) {
    $logPath = Join-Path $logsDir "$name-$runStamp.log"
    $pidPath = Join-Path $logsDir "$name.pid"
    Set-Content -Path (Join-Path $logsDir "$name.log.path") -Value $logPath -Encoding UTF8
    $workDirQuoted = Quote-ForPowerShell $workDir
    $logPathQuoted = Quote-ForPowerShell $logPath
    $wrapped = "Set-Location -LiteralPath $workDirQuoted; $command *> $logPathQuoted"
    $process = Start-Process -FilePath "powershell.exe" `
        -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", $wrapped) `
        -WindowStyle Hidden `
        -PassThru
    Set-Content -Path $pidPath -Value $process.Id -Encoding ASCII
    Write-Host "[STARTED] $name pid=$($process.Id), log=$logPath"
}

$docker = Get-DockerCommand
Write-Host "[RUN] docker compose up -d"
& $docker compose -f (Join-Path $root "docker-compose.yml") up -d

Write-Host "[RUN] stop old demo processes if any"
& (Join-Path $PSScriptRoot "stop-full-demo.ps1")

$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"
$backendPort = Get-AvailablePort 8080 18080
$frontendPort = Get-AvailablePort 5173 15173
$backendUrl = "http://localhost:$backendPort"
$frontendUrl = "http://localhost:$frontendPort"

Set-Content -Path (Join-Path $logsDir "backend.url") -Value $backendUrl -Encoding ASCII
Set-Content -Path (Join-Path $logsDir "frontend.url") -Value $frontendUrl -Encoding ASCII

Start-DemoProcess "backend" $backendDir "`$env:SPRING_PROFILES_ACTIVE='local'; `$env:BACKEND_PORT='$backendPort'; mvn spring-boot:run"
Start-DemoProcess "frontend" $frontendDir "`$env:VITE_BACKEND_URL='$backendUrl'; npm install; npm run dev -- --host 127.0.0.1 --port $frontendPort"

Write-Host ""
Write-Host "URLs:"
Write-Host "Frontend: $frontendUrl"
Write-Host "Backend:  $backendUrl/api/health"
Write-Host "Swagger:  $backendUrl/swagger-ui.html"
Write-Host ""
Write-Host "After the backend is ready, run:"
Write-Host ".\scripts\smoke-demo.ps1 -RunDemo"
Write-Host ""
Write-Host "Stop demo processes:"
Write-Host ".\scripts\stop-full-demo.ps1"
