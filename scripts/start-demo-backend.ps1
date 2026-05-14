param(
    [int]$Port = 18080
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location (Join-Path $root "backend")

mvn "-Dmaven.repo.local=.\.m2repo" `
    spring-boot:run `
    "-Dspring-boot.run.profiles=test" `
    "-Dspring-boot.run.useTestClasspath=true" `
    "-Dspring-boot.run.arguments=--server.port=$Port"
