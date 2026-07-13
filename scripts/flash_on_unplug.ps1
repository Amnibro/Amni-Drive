$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\release\app-release.apk"
function Get-Serial {
  $line = & $adb devices | Where-Object { $_ -match "\tdevice$" } | Select-Object -First 1
  if (-not $line) { return $null }
  return ($line -split "`t")[0].Trim()
}
Write-Host "watching for AA session drop (accessory -> none)..."
$deadline = (Get-Date).AddHours(4)
while ((Get-Date) -lt $deadline) {
  $s = Get-Serial
  if ($s) {
    $fn = & $adb -s $s shell "dumpsys usb 2>/dev/null | grep current_functions= | head -1" 2>$null | Out-String
    if ($fn -and $fn -notmatch "ACCESSORY") {
      Write-Host "SESSION DROPPED ($($fn.Trim())) - flashing v1.7.4"
      Start-Sleep 3
      & $adb -s $s install -r -g -i com.android.vending $apk 2>&1 | Out-Host
      & $adb -s $s shell cmd notification allow_listener com.amni.drive/com.amni.drive.media.AmniNotifListener 2>&1 | Out-Host
      & $adb -s $s shell appops set com.amni.drive SYSTEM_ALERT_WINDOW allow 2>$null
      & $adb -s $s shell appops set com.amni.drive USE_FULL_SCREEN_INTENT allow 2>$null
      & $adb -s $s shell dumpsys package com.amni.drive 2>$null | Select-String "versionName=" | Select-Object -First 1 | Out-Host
      & $adb -s $s shell monkey -p com.amni.drive -c android.intent.category.LAUNCHER 1 2>$null | Out-Null
      Write-Host "FLASH_DONE"
      exit 0
    }
  }
  Start-Sleep 20
}
Write-Host "TIMEOUT no unplug in 4h"
exit 1
