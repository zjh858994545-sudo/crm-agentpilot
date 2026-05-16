param(
    [string]$Container = "agentpilot-postgres",
    [string]$Database = $env:POSTGRES_DB,
    [string]$User = $env:POSTGRES_USER,
    [string]$OutputDir = "",
    [string]$FilePrefix = "agentpilot-postgres"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$root = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($Database)) {
    $Database = "agentpilot"
}
if ([string]::IsNullOrWhiteSpace($User)) {
    $User = "agentpilot"
}
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Join-Path $root "backups\postgres"
}

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

$docker = Get-DockerCommand
$isRunning = (& $docker inspect -f "{{.State.Running}}" $Container 2>$null)
if ($LASTEXITCODE -ne 0 -or ($isRunning -join "").Trim() -ne "true") {
    throw "PostgreSQL container '$Container' is not running. Start the database first."
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$fileName = "$FilePrefix-$timestamp.dump"
$backupPath = Join-Path $OutputDir $fileName
$containerPath = "/tmp/$fileName"

Write-Host "[BACKUP] Dumping database '$Database' from $Container"
& $docker exec $Container sh -lc "pg_dump -U '$User' -d '$Database' -Fc -f '$containerPath'"
if ($LASTEXITCODE -ne 0) {
    throw "pg_dump failed."
}

& $docker cp "${Container}:$containerPath" $backupPath
if ($LASTEXITCODE -ne 0) {
    throw "docker cp failed."
}

& $docker exec $Container rm -f $containerPath | Out-Null

$sizeMb = [Math]::Round((Get-Item -LiteralPath $backupPath).Length / 1MB, 2)
Write-Host "[OK] Backup created: $backupPath ($sizeMb MB)"
Write-Host "[NEXT] Restore test command:"
Write-Host "  .\scripts\restore-postgres.ps1 -BackupFile `"$backupPath`" -ConfirmRestore"
