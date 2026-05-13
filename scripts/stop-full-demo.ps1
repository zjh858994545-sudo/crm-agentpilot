$ErrorActionPreference = "Continue"

$root = Split-Path -Parent $PSScriptRoot
$logsDir = Join-Path $root ".demo-logs"

function Stop-ProcessTree($processId) {
    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $processId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree ([int]$child.ProcessId)
    }
    $process = Get-Process -Id ([int]$processId) -ErrorAction SilentlyContinue
    if ($process) {
        Stop-Process -Id ([int]$processId) -Force
    }
}

foreach ($name in @("backend", "frontend")) {
    $pidPath = Join-Path $logsDir "$name.pid"
    if (-not (Test-Path $pidPath)) {
        Write-Host "[SKIP] $name pid file not found"
        continue
    }
    $processId = Get-Content -Path $pidPath -ErrorAction SilentlyContinue
    if ([string]::IsNullOrWhiteSpace($processId)) {
        Write-Host "[SKIP] $name pid file is empty"
        continue
    }
    $process = Get-Process -Id ([int]$processId) -ErrorAction SilentlyContinue
    if ($process) {
        Stop-ProcessTree ([int]$processId)
        Write-Host "[STOPPED] $name pid=$processId"
    } else {
        Write-Host "[SKIP] $name pid=$processId is not running"
    }
}
