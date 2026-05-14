param(
    [switch]$ResetData
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

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

$docker = Get-DockerCommand
$args = @(
    "compose",
    "-f", (Join-Path $root "docker-compose.yml"),
    "-f", (Join-Path $root "docker-compose.full.yml"),
    "down"
)

if ($ResetData) {
    $args += "-v"
}

& $docker @args
