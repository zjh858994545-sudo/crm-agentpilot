$ErrorActionPreference = "Stop"

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
    throw "Docker CLI was not found. Start Docker Desktop or add docker.exe to PATH."
}

function Quote-ForPowerShell($value) {
    return "'" + ($value -replace "'", "''") + "'"
}

function Start-DemoProcess($name, $workDir, $command) {
    $logPath = Join-Path $logsDir "$name.log"
    $pidPath = Join-Path $logsDir "$name.pid"
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

Start-DemoProcess "backend" $backendDir "`$env:SPRING_PROFILES_ACTIVE='local'; mvn spring-boot:run"
Start-DemoProcess "frontend" $frontendDir "npm install; npm run dev -- --host 127.0.0.1"

Write-Host ""
Write-Host "URLs:"
Write-Host "Frontend: http://localhost:5173"
Write-Host "Backend:  http://localhost:8080/api/health"
Write-Host "Swagger:  http://localhost:8080/swagger-ui.html"
Write-Host ""
Write-Host "After the backend is ready, run:"
Write-Host ".\scripts\smoke-demo.ps1 -RunDemo"
Write-Host ""
Write-Host "Stop demo processes:"
Write-Host ".\scripts\stop-full-demo.ps1"
