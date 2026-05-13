$ErrorActionPreference = "Stop"

$baseUrl = $env:AGENTPILOT_BASE_URL
if ([string]::IsNullOrWhiteSpace($baseUrl)) {
    $baseUrl = "http://localhost:8080"
}

function Convert-FromUtf8Base64($value) {
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($value))
}

$analyzePrompt = Convert-FromUtf8Base64 "5biu5oiR5YiG5p6Q5LiA5LiL576O5a625oi/5Lqn6L+Z5Liq5a6i5oi377yM5piO5aSp5bqU6K+l5oCO5LmI6Lef6L+b77yf"
$taskPrompt = Convert-FromUtf8Base64 "5biu5oiR5Yib5bu65piO5aSp5LiK5Y2IMTDngrnot5/ov5vnvo7lrrbmiL/kuqfnu63otLnnmoTku7vliqHjgII="
$knowledgeQuestion = Convert-FromUtf8Base64 "5a6i5oi35auM5aWX6aSQ6LS15bm25ouF5b+D57ut6LS55pWI5p6c5pe277yM6ZSA5ZSu5bqU6K+l5oCO5LmI5Zue5aSN77yf"
$callText = Convert-FromUtf8Base64 "5a6i5oi36K+05aWX6aSQ5pyJ54K56LS177yM5ouF5b+D57ut6LS55ZCO5rKh5pyJ5pWI5p6c44CC6ZSA5ZSu6KGo56S65Y+v5Lul5biu5a6i5oi35LqJ5Y+W5LyY5oOg77yM5bm26K+05piO5Lya5Zyo5piO5aSp5LiK5Y2I5o+Q5L6b5LiK5pyI5pud5YWJ5pWw5o2u5ZKM5ZCM6KGM5qGI5L6L77yM5L2G5LiN5Lya5om/6K+65LiA5a6a5oiQ5Lqk44CC"

function Show-Step($name) {
    Write-Host ""
    Write-Host "==== $name ===="
}

function Invoke-AgentPilot($method, $path, $body = $null) {
    $uri = "$baseUrl$path"
    if ($null -eq $body) {
        return Invoke-RestMethod -Method $method -Uri $uri
    }
    return Invoke-RestMethod -Method $method -Uri $uri -ContentType "application/json; charset=utf-8" -Body ($body | ConvertTo-Json -Depth 10)
}

Show-Step "1. Health"
(Invoke-AgentPilot Get "/api/health").data | ConvertTo-Json -Depth 8

Show-Step "2. Lead Recommendation"
(Invoke-AgentPilot Get "/api/leads/recommend?topK=3").data | ConvertTo-Json -Depth 8

Show-Step "3. Agent Reads CRM And RAG"
$agentRead = Invoke-AgentPilot Post "/api/agent/chat" @{
    userId = 1
    salesRepId = 1
    customerId = 1001
    message = $analyzePrompt
}
$agentRead.data | ConvertTo-Json -Depth 8

Show-Step "4. Agent Proposes A CRM Write"
$agentWrite = Invoke-AgentPilot Post "/api/agent/chat" @{
    userId = 1
    salesRepId = 1
    customerId = 1001
    message = $taskPrompt
}
$agentWrite.data | ConvertTo-Json -Depth 8

if ($agentWrite.data.confirmationId) {
    Show-Step "5. Confirm CRM Write"
    (Invoke-AgentPilot Post "/api/agent/confirmations/$($agentWrite.data.confirmationId)/confirm" @{ userId = 1 }).data | ConvertTo-Json -Depth 8
}

Show-Step "6. Knowledge Ask"
(Invoke-AgentPilot Post "/api/knowledge/ask" @{
    question = $knowledgeQuestion
    topK = 5
}).data | ConvertTo-Json -Depth 8

Show-Step "7. Call Summary"
(Invoke-AgentPilot Post "/api/callcenter/summary" @{
    customerId = 1001
    salesRepId = 1
    leadId = 3001
    text = $callText
}).data | ConvertTo-Json -Depth 8

Show-Step "8. Call Quality Check"
(Invoke-AgentPilot Post "/api/callcenter/quality-check" @{
    customerId = 1001
    salesRepId = 1
    leadId = 3001
    text = $callText
}).data | ConvertTo-Json -Depth 8

Show-Step "9. Evaluation Report"
(Invoke-AgentPilot Post "/api/evaluation/run").data | ConvertTo-Json -Depth 8
