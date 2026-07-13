param(
  [int]$WaitMinutes = 90,
  [int]$Cycles = 3,
  [string]$Apk = ""
)
$ErrorActionPreference = "Continue"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
if (-not $Apk) {
  $Apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
}
$auto = Join-Path $PSScriptRoot "automate_own_stack.ps1"
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logDir = Join-Path $root ("docs\captures\watch_" + $stamp)
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$report = Join-Path $logDir "watch_report.txt"
function W([string]$m) {
  $line = "[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $m
  Write-Host $line
  Add-Content -Path $report -Value $line
}
function NeedSerial {
  foreach ($l in (& $adb devices 2>&1)) {
    if ($l -match "^(\S+)\s+device") { return $Matches[1] }
  }
  return $null
}
function TryMdnsConnect {
  $out = (& $adb mdns services 2>&1 | Out-String)
  if ($out -match "(\d+\.\d+\.\d+\.\d+):(\d+)") {
    $ep = $Matches[1] + ":" + $Matches[2]
    W ("mdns connect " + $ep)
    & $adb connect $ep 2>&1 | ForEach-Object { W ("  " + $_) }
    Start-Sleep 2
  }
  foreach ($h in @("100.116.191.230:37593", "100.116.191.230:5555")) {
    & $adb connect $h 2>&1 | Out-Null
  }
}
W "=== Amni wait-and-auto-test ==="
W ("root=" + $root)
W ("apk=" + $Apk + " exists=" + (Test-Path $Apk))
W ("wait up to " + $WaitMinutes + " minutes")
$deadline = (Get-Date).AddMinutes($WaitMinutes)
$serial = $null
while ((Get-Date) -lt $deadline) {
  & $adb start-server 2>&1 | Out-Null
  $serial = NeedSerial
  if ($serial) { break }
  TryMdnsConnect
  $serial = NeedSerial
  if ($serial) { break }
  W "waiting for device..."
  Start-Sleep 15
}
if (-not $serial) {
  W "TIMEOUT: no ADB device. Enable wireless debugging and leave phone plugged in car."
  exit 2
}
W ("DEVICE " + $serial)
& $adb -s $serial shell dumpsys usb 2>&1 | Select-String "current_functions=|model=|kernel_state" | ForEach-Object { W ("USB " + $_.Line.Trim()) }
& $adb -s $serial shell "ps -A" 2>&1 | Select-String "gearhead|amni.drive" | ForEach-Object { W ("PROC " + $_.Line.Trim()) }
if (Test-Path $Apk) {
  W "install -r debug APK"
  $inst = & $adb -s $serial install -r -t $Apk 2>&1 | Out-String
  W $inst.Trim()
  W "force-stop amni so new code is live"
  & $adb -s $serial shell am force-stop com.amni.drive 2>&1 | Out-Null
} else {
  W "APK missing - using package already on device"
}
& $adb -s $serial shell dumpsys package com.amni.drive 2>&1 | Select-String "versionName|versionCode" | ForEach-Object { W ("PKG " + $_.Line.Trim()) }
W "launch OWN_STACK + force-stop gearhead via automate_own_stack.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $auto -Serial $serial -WaitSec 30 -Cycles $Cycles
$cap = Get-ChildItem (Join-Path $root "docs\captures") -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($cap) {
  W ("latest capture folder: " + $cap.FullName)
  Copy-Item -Path (Join-Path $cap.FullName "*") -Destination $logDir -Force -ErrorAction SilentlyContinue
}
Start-Sleep 2
W "post-run probe log / logcat"
& $adb -s $serial logcat -d -t 80 -s Amni.AoaProbe:I 2>&1 | ForEach-Object { W ("LOG " + $_) }
& $adb -s $serial shell "tail -40 /sdcard/Android/data/com.amni.drive/files/aoa_probe/probe-*.log 2>&1" 2>&1 | ForEach-Object { W ("PROBE " + $_) }
& $adb -s $serial shell dumpsys usb 2>&1 | Select-String "current_functions=|model=" | ForEach-Object { W ("USB2 " + $_.Line.Trim()) }
W ("DONE. Open " + $logDir)
exit 0
