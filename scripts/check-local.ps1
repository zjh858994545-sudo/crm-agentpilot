$ErrorActionPreference = "Continue"

function Test-Command($name, $commandArgs = @("--version")) {
    Write-Host ""
    Write-Host "==== $name ===="
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($null -eq $cmd) {
        Write-Host "NOT FOUND"
        return
    }
    Write-Host $cmd.Source
    & $name @commandArgs
}

Test-Command "java" @("-version")
Test-Command "mvn" @("-version")
Test-Command "node" @("--version")
Test-Command "npm" @("--version")
Test-Command "docker" @("--version")

Write-Host ""
Write-Host "If docker is NOT FOUND, install Docker Desktop or add Docker CLI to PATH before running docker compose."
