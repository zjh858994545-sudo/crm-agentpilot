param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile,
    [string]$Container = "agentpilot-postgres",
    [string]$Database = $env:POSTGRES_DB,
    [string]$User = $env:POSTGRES_USER,
    [switch]$ConfirmRestore
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

if (-not $ConfirmRestore) {
    throw "Restore is destructive. Rerun with -ConfirmRestore after stopping application traffic and verifying the backup file."
}
if (-not (Test-Path -LiteralPath $BackupFile)) {
    throw "Backup file not found: $BackupFile"
}
if ([string]::IsNullOrWhiteSpace($Database)) {
    $Database = "agentpilot"
}
if ([string]::IsNullOrWhiteSpace($User)) {
    $User = "agentpilot"
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

$fileName = Split-Path -Leaf $BackupFile
$containerPath = "/tmp/$fileName"

Write-Host "[RESTORE] Copying backup into $Container"
& $docker cp $BackupFile "${Container}:$containerPath"
if ($LASTEXITCODE -ne 0) {
    throw "docker cp failed."
}

Write-Host "[RESTORE] Restoring '$Database' from $fileName"
Write-Host "[WARN] This will drop and recreate objects included in the backup."
& $docker exec $Container sh -lc "pg_restore -U '$User' -d '$Database' --clean --if-exists --no-owner --no-privileges '$containerPath'"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[HINT] If restore failed because tables are locked, stop backend/frontend traffic and retry."
    throw "pg_restore failed."
}

& $docker exec $Container rm -f $containerPath | Out-Null

Write-Host "[OK] Restore completed."
Write-Host "[NEXT] Run:"
Write-Host "  .\scripts\smoke-demo.ps1 -RunDemo"
