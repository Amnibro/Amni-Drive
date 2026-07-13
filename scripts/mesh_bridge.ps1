# Poll Amni-Drive Mesh Dev Bridge over Nord Meshnet / LAN (no ADB required).
param(
  [string]$Ip = "100.116.191.230",
  [int]$Port = 7931,
  [string]$Token = "",
  [int]$Minutes = 30,
  [int]$IntervalSec = 6
)
$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $root ("docs\captures\mesh_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
function Try-Get([string]$url) {
  try {
    return Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 4
  } catch {
    return $null
  }
}
Write-Host "=== mesh bridge poll $Ip`:$Port ==="
Write-Host "captures: $outDir"
if (-not $Token) {
  Write-Host "No token yet — open Amni-Drive Settings › AMNI WIRE for token, or pass -Token"
}
$end = (Get-Date).AddMinutes($Minutes)
$n = 0
while ((Get-Date) -lt $end) {
  $n++
  $base = "http://${Ip}:${Port}"
  $q = if ($Token) { "?token=$Token" } else { "" }
  $h = Try-Get "$base/health$q"
  if (-not $h) {
    # probe without token for reachability noise
    $h2 = Try-Get "$base/health"
    Write-Host ("{0:HH:mm:ss} try=$n offline/unreachable (or need token) status={1}" -f (Get-Date), $(if ($h2) { $h2.StatusCode } else { "null" }))
    Start-Sleep $IntervalSec
    continue
  }
  Write-Host ("{0:HH:mm:ss} HEALTH {1}" -f (Get-Date), $h.Content)
  $h.Content | Set-Content (Join-Path $outDir "health.json")
  if ($Token) {
    $st = Try-Get "$base/status?token=$Token"
    if ($st) {
      Write-Host "STATUS $($st.Content)"
      $st.Content | Set-Content (Join-Path $outDir ("status_{0:HHmmss}.json" -f (Get-Date)))
    }
    $w = Try-Get "$base/logs/watch?token=$Token"
    if ($w) { $w.Content | Set-Content (Join-Path $outDir "watch.log") }
    $p = Try-Get "$base/logs/probe?token=$Token"
    if ($p) { $p.Content | Set-Content (Join-Path $outDir "probe.log") }
    $l = Try-Get "$base/logcat?token=$Token&tag=Amni"
    if ($l) { $l.Content | Set-Content (Join-Path $outDir "logcat.txt") }
  }
  Start-Sleep $IntervalSec
}
Write-Host "done $outDir"
