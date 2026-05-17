param(
    [string]$ProjectRoot = "",
    [switch]$CheckPull,
    [switch]$Json
)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = Split-Path -Parent $PSScriptRoot
}

$results = New-Object System.Collections.Generic.List[object]

function Add-Check {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail,
        [string]$Action = ""
    )
    $results.Add([pscustomobject]@{
        name = $Name
        status = $Status
        detail = $Detail
        action = $Action
    }) | Out-Null
}

function Test-PortInUse($port) {
    $connection = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $connection) {
        return $null
    }
    $process = Get-Process -Id $connection.OwningProcess -ErrorAction SilentlyContinue
    return [pscustomobject]@{
        port = $port
        pid = $connection.OwningProcess
        process = $process.ProcessName
        path = $process.Path
    }
}

function Invoke-Docker {
    param([string[]]$Arguments, [int]$TimeoutSeconds = 20)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "docker"
    foreach ($arg in $Arguments) {
        $psi.ArgumentList.Add($arg)
    }
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.UseShellExecute = $false
    $process = [System.Diagnostics.Process]::Start($psi)
    if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
        try { $process.Kill($true) } catch {}
        throw "docker $($Arguments -join ' ') timed out after ${TimeoutSeconds}s"
    }
    return [pscustomobject]@{
        exitCode = $process.ExitCode
        stdout = $process.StandardOutput.ReadToEnd()
        stderr = $process.StandardError.ReadToEnd()
    }
}

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if ($dockerCommand) {
    Add-Check "Docker CLI" "PASS" $dockerCommand.Source
} else {
    Add-Check "Docker CLI" "FAIL" "docker.exe not found in PATH" "Start Docker Desktop or add Docker CLI to PATH."
}

$dockerDesktop = Get-Process "Docker Desktop" -ErrorAction SilentlyContinue | Select-Object -First 1
$dockerBackend = Get-Process "com.docker.backend" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($dockerDesktop -or $dockerBackend) {
    Add-Check "Docker Desktop process" "PASS" "Docker Desktop/backend process is running."
} else {
    Add-Check "Docker Desktop process" "FAIL" "Docker Desktop process is not running." "Open Docker Desktop and wait until it says Engine running."
}

if ($dockerCommand) {
    try {
        $version = Invoke-Docker @("version", "--format", "{{.Server.Version}}") 15
        if ($version.exitCode -eq 0 -and -not [string]::IsNullOrWhiteSpace($version.stdout)) {
            Add-Check "Docker API" "PASS" "Server version $($version.stdout.Trim())"
        } else {
            Add-Check "Docker API" "FAIL" ($version.stderr.Trim()) "Restart Docker Desktop. If it still hangs, restart Windows."
        }
    } catch {
        Add-Check "Docker API" "FAIL" $_.Exception.Message "Restart Docker Desktop. If docker commands keep timing out, restart Windows."
    }
}

$drives = @("C", "F") | ForEach-Object {
    Get-PSDrive $_ -ErrorAction SilentlyContinue
}
foreach ($drive in $drives) {
    $freeGb = [math]::Round($drive.Free / 1GB, 2)
    $status = if ($freeGb -lt 5) { "WARN" } else { "PASS" }
    $action = if ($freeGb -lt 5) { "Move Docker/Codex/temp/cache data to F: or clean old build artifacts." } else { "" }
    Add-Check "Disk $($drive.Name): free space" $status "${freeGb}GB free" $action
}

$proxyHints = @()
foreach ($name in @("HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY")) {
    $value = [Environment]::GetEnvironmentVariable($name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = [Environment]::GetEnvironmentVariable($name, "User")
    }
    if (-not [string]::IsNullOrWhiteSpace($value)) {
        $proxyHints += "${name}=${value}"
    }
}
$proxy7890 = Test-NetConnection -ComputerName 127.0.0.1 -Port 7890 -InformationLevel Quiet -WarningAction SilentlyContinue
if ($proxyHints.Count -gt 0 -or $proxy7890) {
    Add-Check "Proxy" "PASS" (($proxyHints + $(if ($proxy7890) { "127.0.0.1:7890 open" } else { $null })) -join "; ")
} else {
    Add-Check "Proxy" "WARN" "No local proxy detected." "If Docker Hub pull fails in China network, configure Docker Desktop proxy: http://127.0.0.1:7890."
}

foreach ($port in @(5432, 6379, 9092, 18080, 15173, 5173)) {
    $owner = Test-PortInUse $port
    if ($owner) {
        Add-Check "Port $port" "WARN" "In use by pid=$($owner.pid) process=$($owner.process)" "Stop the process or choose another port."
    } else {
        Add-Check "Port $port" "PASS" "Available"
    }
}

if (Test-Path (Join-Path $ProjectRoot "docker-compose.yml")) {
    Add-Check "Compose files" "PASS" "docker-compose.yml found"
} else {
    Add-Check "Compose files" "FAIL" "docker-compose.yml not found at $ProjectRoot"
}

if ($dockerCommand) {
    try {
        $compose = Invoke-Docker @("compose", "-f", (Join-Path $ProjectRoot "docker-compose.yml"), "config", "--quiet") 30
        if ($compose.exitCode -eq 0) {
            Add-Check "Compose config" "PASS" "Base compose config is valid."
        } else {
            Add-Check "Compose config" "FAIL" ($compose.stderr.Trim()) "Fix docker-compose.yml syntax or environment variables."
        }
    } catch {
        Add-Check "Compose config" "WARN" $_.Exception.Message "Docker API may be stuck; restart Docker Desktop."
    }
}

if ($CheckPull -and $dockerCommand) {
    foreach ($image in @("pgvector/pgvector:pg16", "redis:7.2-alpine", "apache/kafka:3.7.2")) {
        try {
            $pull = Invoke-Docker @("pull", $image) 120
            if ($pull.exitCode -eq 0) {
                Add-Check "Pull $image" "PASS" "Image pulled."
            } else {
                Add-Check "Pull $image" "FAIL" ($pull.stderr.Trim()) "Check Docker Desktop proxy and Docker Hub connectivity."
            }
        } catch {
            Add-Check "Pull $image" "FAIL" $_.Exception.Message "Check Docker Desktop proxy and network."
        }
    }
}

if ($Json) {
    $results | ConvertTo-Json -Depth 4
    exit
}

Write-Host "CRM-AgentPilot Docker diagnostics"
Write-Host "ProjectRoot: $ProjectRoot"
Write-Host ""

foreach ($item in $results) {
    $color = switch ($item.status) {
        "PASS" { "Green" }
        "WARN" { "Yellow" }
        "FAIL" { "Red" }
        default { "White" }
    }
    Write-Host ("[{0}] {1}: {2}" -f $item.status, $item.name, $item.detail) -ForegroundColor $color
    if (-not [string]::IsNullOrWhiteSpace($item.action)) {
        Write-Host ("      -> {0}" -f $item.action) -ForegroundColor DarkGray
    }
}

$failCount = ($results | Where-Object { $_.status -eq "FAIL" }).Count
$warnCount = ($results | Where-Object { $_.status -eq "WARN" }).Count
Write-Host ""
Write-Host "Summary: $failCount FAIL, $warnCount WARN, $($results.Count) checks."
if ($failCount -gt 0) {
    exit 1
}
