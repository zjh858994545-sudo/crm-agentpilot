param(
  [string]$DistPath = $(Join-Path (Split-Path -Parent $PSScriptRoot) "frontend\dist"),
  [int]$MaxSingleAssetKb = 1100,
  [int]$MaxTotalJsGzipKb = 850,
  [int]$MaxTotalCssGzipKb = 80
)

$ErrorActionPreference = "Stop"

function Get-GzipSizeBytes {
  param([string]$Path)
  $inputBytes = [System.IO.File]::ReadAllBytes($Path)
  $memoryStream = New-Object System.IO.MemoryStream
  try {
    $gzipStream = New-Object System.IO.Compression.GzipStream($memoryStream, [System.IO.Compression.CompressionLevel]::Optimal, $true)
    try {
      $gzipStream.Write($inputBytes, 0, $inputBytes.Length)
    } finally {
      $gzipStream.Dispose()
    }
    return $memoryStream.Length
  } finally {
    $memoryStream.Dispose()
  }
}

function Format-Kb {
  param([double]$Bytes)
  return [Math]::Round($Bytes / 1024, 1)
}

if (-not (Test-Path -LiteralPath $DistPath)) {
  throw "Frontend dist path does not exist: $DistPath. Run npm run build first."
}

$assetPath = Join-Path $DistPath "assets"
if (-not (Test-Path -LiteralPath $assetPath)) {
  throw "Frontend assets path does not exist: $assetPath."
}

$assets = Get-ChildItem -LiteralPath $assetPath -File | Where-Object {
  $_.Extension -in @(".js", ".css")
}

if (-not $assets -or $assets.Count -eq 0) {
  throw "No frontend JS/CSS assets found in $assetPath."
}

$failed = 0
$totalJsGzip = 0
$totalCssGzip = 0

Write-Host "CRM-AgentPilot frontend bundle budget"
Write-Host "DistPath: $DistPath"

foreach ($asset in ($assets | Sort-Object Length -Descending)) {
  $rawKb = Format-Kb $asset.Length
  $gzipBytes = Get-GzipSizeBytes $asset.FullName
  $gzipKb = Format-Kb $gzipBytes
  if ($asset.Extension -eq ".js") {
    $totalJsGzip += $gzipBytes
  }
  if ($asset.Extension -eq ".css") {
    $totalCssGzip += $gzipBytes
  }

  if ($rawKb -gt $MaxSingleAssetKb) {
    $failed++
    Write-Host "[FAIL] $($asset.Name) raw=${rawKb}KB gzip=${gzipKb}KB exceeds single asset budget ${MaxSingleAssetKb}KB" -ForegroundColor Red
  } else {
    Write-Host "[OK] $($asset.Name) raw=${rawKb}KB gzip=${gzipKb}KB"
  }
}

$totalJsGzipKb = Format-Kb $totalJsGzip
$totalCssGzipKb = Format-Kb $totalCssGzip

if ($totalJsGzipKb -gt $MaxTotalJsGzipKb) {
  $failed++
  Write-Host "[FAIL] total JS gzip ${totalJsGzipKb}KB exceeds budget ${MaxTotalJsGzipKb}KB" -ForegroundColor Red
} else {
  Write-Host "[OK] total JS gzip ${totalJsGzipKb}KB <= ${MaxTotalJsGzipKb}KB"
}

if ($totalCssGzipKb -gt $MaxTotalCssGzipKb) {
  $failed++
  Write-Host "[FAIL] total CSS gzip ${totalCssGzipKb}KB exceeds budget ${MaxTotalCssGzipKb}KB" -ForegroundColor Red
} else {
  Write-Host "[OK] total CSS gzip ${totalCssGzipKb}KB <= ${MaxTotalCssGzipKb}KB"
}

if ($failed -gt 0) {
  throw "$failed frontend bundle budget checks failed."
}

Write-Host "Frontend bundle budget passed."
