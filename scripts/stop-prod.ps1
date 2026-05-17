param(
    [string]$EnvFile = ".env.production"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envPath = if ([System.IO.Path]::IsPathRooted($EnvFile)) { $EnvFile } else { Join-Path $root $EnvFile }

& docker compose `
    --env-file $envPath `
    -f (Join-Path $root "docker-compose.yml") `
    -f (Join-Path $root "docker-compose.prod.yml") `
    down

if ($LASTEXITCODE -ne 0) {
    throw "docker compose down failed"
}
