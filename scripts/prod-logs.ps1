param(
    [string]$Service = "",
    [int]$Tail = 200,
    [string]$EnvFile = ".env.production"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $root $EnvFile }

$args = @(
    "compose",
    "--env-file", $envPath,
    "-f", (Join-Path $root "docker-compose.yml"),
    "-f", (Join-Path $root "docker-compose.prod.yml"),
    "logs", "--tail", "$Tail", "-f"
)
if (-not [string]::IsNullOrWhiteSpace($Service)) {
    $args += $Service
}

& docker @args
