$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\release\app-release.apk"
$ip = "100.116.191.230"
$ports = @(49111, 46521)
function Get-Serial {
  $line = & $adb devices | Where-Object { $_ -match "\tdevice$" } | Select-Object -First 1
  if (-not $line) { return $null }
  return ($line -split "`t")[0].Trim()
}
Write-Host "waiting for device to come online..."
$deadline = (Get-Date).AddHours(4)
$tick = 0
while ((Get-Date) -lt $deadline) {
  $s = Get-Serial
  if ($s) {
    $fn = & $adb -s $s shell "dumpsys usb 2>/dev/null | grep current_functions= | head -1" 2>$null | Out-String
    if ($fn -match "ACCESSORY") { Write-Host "in car session - waiting"; Start-Sleep 30; continue }
    Write-Host "ONLINE ($s) - flashing v1.8.0"
    & $adb -s $s install -r -g -i com.android.vending $apk 2>&1 | Out-Host
    & $adb -s $s shell cmd notification allow_listener com.amni.drive/com.amni.drive.media.AmniNotifListener 2>&1 | Out-Host
    & $adb -s $s shell "cmd notification allow_listener com.amni.drive/com.amni.drive.media.AmniNotifListener; dumpsys notification 2>/dev/null | grep -i 'amni' | head -3" 2>$null | Out-Host
    & $adb -s $s shell appops set com.amni.drive SYSTEM_ALERT_WINDOW allow 2>$null
    & $adb -s $s shell appops set com.amni.drive USE_FULL_SCREEN_INTENT allow 2>$null
    & $adb -s $s shell dumpsys package com.amni.drive 2>$null | Select-String "versionName=" | Select-Object -First 1 | Out-Host
    Write-Host "FLASH_DONE_1.8.0"
    exit 0
  }
  $tick++
  if ($tick % 3 -eq 0) {
    & $adb kill-server 2>$null; Start-Sleep 1; & $adb start-server 2>$null; Start-Sleep 1
    foreach ($p in $ports) { & $adb connect "${ip}:${p}" 2>$null | Out-Null }
    & $adb mdns services 2>$null | Out-Null
  }
  Start-Sleep 20
}
Write-Host "TIMEOUT"
exit 1
