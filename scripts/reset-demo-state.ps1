$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

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
    throw "Docker CLI was not found. Start Docker Desktop or add docker.exe to PATH."
}

$docker = Get-DockerCommand
$container = "agentpilot-postgres"

$isRunning = (& $docker inspect -f "{{.State.Running}}" $container 2>$null)
if ($LASTEXITCODE -ne 0 -or ($isRunning -join "").Trim() -ne "true") {
    throw "PostgreSQL container '$container' is not running. Start the demo first: .\scripts\start-docker-demo.ps1"
}

Write-Host "[RESET] Cleaning generated demo data in $container"

$sql = @"
BEGIN;

SELECT 'before_agent_runs' AS metric, COUNT(*) AS value FROM crm_agent_run;
SELECT 'before_pending_confirmations' AS metric, COUNT(*) AS value FROM crm_agent_confirmation WHERE status = 'PENDING';
SELECT 'before_agent_tasks' AS metric, COUNT(*) AS value FROM crm_task WHERE source = 'AGENT' OR idempotency_key LIKE 'agent-task-%';
SELECT 'before_agent_contact_logs' AS metric, COUNT(*) AS value FROM crm_contact_log WHERE idempotency_key LIKE 'agent-contact-log-%';

DELETE FROM crm_task
WHERE source = 'AGENT'
   OR idempotency_key LIKE 'agent-task-%';

DELETE FROM crm_contact_log
WHERE idempotency_key LIKE 'agent-contact-log-%';

TRUNCATE TABLE
    crm_agent_feedback,
    crm_agent_confirmation,
    crm_agent_tool_call,
    crm_agent_run,
    crm_agent_session,
    agent_outbox_event
RESTART IDENTITY;

SELECT 'after_agent_runs' AS metric, COUNT(*) AS value FROM crm_agent_run;
SELECT 'after_pending_confirmations' AS metric, COUNT(*) AS value FROM crm_agent_confirmation WHERE status = 'PENDING';
SELECT 'after_agent_tasks' AS metric, COUNT(*) AS value FROM crm_task WHERE source = 'AGENT' OR idempotency_key LIKE 'agent-task-%';
SELECT 'after_agent_contact_logs' AS metric, COUNT(*) AS value FROM crm_contact_log WHERE idempotency_key LIKE 'agent-contact-log-%';

COMMIT;
"@

$sql | & $docker exec -i $container psql -U agentpilot -d agentpilot -v ON_ERROR_STOP=1

Write-Host "[OK] Demo state reset complete."
Write-Host "Next:"
Write-Host "  .\scripts\smoke-demo.ps1 -RunDemo"
Write-Host "  or open the frontend URL from .demo-logs\frontend.url"
