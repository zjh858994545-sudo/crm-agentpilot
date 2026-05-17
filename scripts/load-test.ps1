param(
    [string]$BaseUrl = "",
    [ValidateSet("health", "dashboard", "agent")]
    [string]$Scenario = "health",
    [int]$Requests = 100,
    [int]$Concurrency = 10,
    [string]$Token = "",
    [string]$ReportDir = "ops\reports"
)

$ErrorActionPreference = "Stop"

function Resolve-BaseUrl {
    param([string]$InputUrl)
    if ($InputUrl) {
        return $InputUrl.TrimEnd("/")
    }
    $urlFile = Join-Path (Get-Location) ".demo-logs\backend.url"
    if (Test-Path $urlFile) {
        return (Get-Content $urlFile -Raw).Trim().TrimEnd("/")
    }
    return "http://localhost:18080"
}

function Percentile {
    param([double[]]$Values, [double]$Percent)
    if (-not $Values -or $Values.Count -eq 0) {
        return 0
    }
    $sorted = $Values | Sort-Object
    $index = [Math]::Ceiling(($Percent / 100.0) * $sorted.Count) - 1
    $index = [Math]::Max(0, [Math]::Min($index, $sorted.Count - 1))
    return [Math]::Round($sorted[$index], 2)
}

function Scenario-Request {
    param([string]$ScenarioName, [string]$RootUrl)
    switch ($ScenarioName) {
        "dashboard" {
            return @{
                Method = "GET"
                Uri = "$RootUrl/api/dashboard/metrics"
                Body = $null
            }
        }
        "agent" {
            return @{
                Method = "POST"
                Uri = "$RootUrl/api/agent/chat"
                Body = (@{
                    sessionId = "load-test"
                    userId = 1
                    salesRepId = 1
                    message = "Analyze customer 1001 and suggest the next follow-up action"
                } | ConvertTo-Json -Depth 6)
            }
        }
        default {
            return @{
                Method = "GET"
                Uri = "$RootUrl/api/health"
                Body = $null
            }
        }
    }
}

$root = Resolve-BaseUrl $BaseUrl
$request = Scenario-Request $Scenario $root
$headers = @{}
if ($Token) {
    $headers["X-AgentPilot-Token"] = $Token
}

New-Item -ItemType Directory -Force $ReportDir | Out-Null

Write-Host "CRM-AgentPilot load test"
Write-Host "BaseUrl:     $root"
Write-Host "Scenario:    $Scenario"
Write-Host "Requests:    $Requests"
Write-Host "Concurrency: $Concurrency"

$allResults = New-Object System.Collections.Generic.List[object]
$next = 1

while ($next -le $Requests) {
    $jobs = @()
    $batchSize = [Math]::Min($Concurrency, $Requests - $next + 1)
    for ($i = 0; $i -lt $batchSize; $i++) {
        $requestNumber = $next
        $jobs += Start-Job -ScriptBlock {
            param($RequestNumber, $Method, $Uri, $Body, $Headers)
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                if ($Body) {
                    $response = Invoke-WebRequest -Method $Method -Uri $Uri -Headers $Headers -ContentType "application/json; charset=utf-8" -Body $Body -TimeoutSec 60
                } else {
                    $response = Invoke-WebRequest -Method $Method -Uri $Uri -Headers $Headers -TimeoutSec 60
                }
                $sw.Stop()
                [pscustomobject]@{
                    request = $RequestNumber
                    ok = $true
                    status = [int]$response.StatusCode
                    latencyMs = [Math]::Round($sw.Elapsed.TotalMilliseconds, 2)
                    error = ""
                }
            } catch {
                $sw.Stop()
                $statusCode = 0
                if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                    $statusCode = [int]$_.Exception.Response.StatusCode
                }
                [pscustomobject]@{
                    request = $RequestNumber
                    ok = $false
                    status = $statusCode
                    latencyMs = [Math]::Round($sw.Elapsed.TotalMilliseconds, 2)
                    error = $_.Exception.Message
                }
            }
        } -ArgumentList $requestNumber, $request.Method, $request.Uri, $request.Body, $headers
        $next++
    }
    $batchResults = $jobs | Wait-Job | Receive-Job
    $jobs | Remove-Job
    foreach ($item in $batchResults) {
        $allResults.Add($item) | Out-Null
    }
    Write-Host ("Completed {0}/{1}" -f $allResults.Count, $Requests)
}

$latencies = @($allResults | ForEach-Object { [double]$_.latencyMs })
$successCount = @($allResults | Where-Object { $_.ok }).Count
$failedCount = $allResults.Count - $successCount
$avg = if ($latencies.Count) { [Math]::Round(($latencies | Measure-Object -Average).Average, 2) } else { 0 }
$p50 = Percentile $latencies 50
$p95 = Percentile $latencies 95
$p99 = Percentile $latencies 99
$statusGroups = $allResults | Group-Object status | Sort-Object Name | ForEach-Object { "| $($_.Name) | $($_.Count) |" }
$errorSamples = $allResults | Where-Object { -not $_.ok -and $_.error } | Select-Object -First 5

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportDir "load-test-$Scenario-$timestamp.md"
$report = @()
$report += "# CRM-AgentPilot Load Test"
$report += ""
$report += "- Time: $(Get-Date -Format s)"
$report += "- BaseUrl: $root"
$report += "- Scenario: $Scenario"
$report += "- Requests: $Requests"
$report += "- Concurrency: $Concurrency"
$report += ""
$report += "## Summary"
$report += ""
$report += "| Metric | Value |"
$report += "|---|---:|"
$report += "| Success | $successCount |"
$report += "| Failed | $failedCount |"
$report += "| Average latency | $avg ms |"
$report += "| P50 latency | $p50 ms |"
$report += "| P95 latency | $p95 ms |"
$report += "| P99 latency | $p99 ms |"
$report += ""
$report += "## Status Codes"
$report += ""
$report += "| Status | Count |"
$report += "|---|---:|"
$report += $statusGroups
if ($errorSamples.Count -gt 0) {
    $report += ""
    $report += "## Error Samples"
    $report += ""
    foreach ($sample in $errorSamples) {
        $report += "- request=$($sample.request), status=$($sample.status), error=$($sample.error)"
    }
}

$report | Set-Content -Path $reportPath -Encoding UTF8
Write-Host ""
Write-Host "Load test report: $reportPath"
Write-Host "Success=$successCount Failed=$failedCount Avg=${avg}ms P95=${p95}ms P99=${p99}ms"

if ($failedCount -gt 0) {
    exit 1
}
