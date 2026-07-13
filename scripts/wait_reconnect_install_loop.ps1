# Wait for wireless ADB, install latest debug APK, start dual-path watch + car loop reports.
param([int]$MaxMinutes = 60)
$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$root = Split-Path $PSScriptRoot -Parent
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$outDir = Join-Path $root ("docs\captures\reconnect_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
function TryConnect {
  $svc = & $adb mdns services 2>$null | Out-String
  $m = [regex]::Match($svc, "(\d+\.\d+\.\d+\.\d+):(\d+)")
  if ($m.Success) {
    $ep = $m.Groups[1].Value + ":" + $m.Groups[2].Value
    Write-Host "mdns connect $ep"
    & $adb connect $ep 2>&1 | Out-Host
  }
  foreach ($ep in @("100.116.191.230:49111","100.116.191.230:5555","192.168.0.96:44401","192.168.0.96:5555")) {
    & $adb connect $ep 2>&1 | Out-Null
  }
}
function HasDevice {
  $d = (& $adb devices) | Out-String
  return ($d -match "\tdevice")
}
Write-Host "waiting for device up to $MaxMinutes min; apk=$apk"
$end = (Get-Date).AddMinutes($MaxMinutes)
$installed = $false
while ((Get-Date) -lt $end) {
  if (-not (HasDevice)) { TryConnect; Start-Sleep 4; continue }
  Write-Host "DEVICE UP $(Get-Date -Format o)"
  if (-not $installed -and (Test-Path $apk)) {
    Write-Host "install..."
    & $adb install -r $apk 2>&1 | Out-Host
    $installed = $true
    & $adb shell am force-stop com.amni.drive
    & $adb shell am start -n com.amni.drive/.MainActivity
    Start-Sleep 2
  }
  $rep = Join-Path $outDir ("tick_" + (Get-Date -Format "HHmmss") + ".txt")
  $buf = @()
  $buf += "time=$(Get-Date -Format o)"
  $buf += (& $adb shell dumpsys package com.amni.drive | Select-String "versionName|versionCode" | Select-Object -First 4 | Out-String)
  $buf += (& $adb shell "ps -A | grep -iE 'gearhead|amni.drive'" | Out-String)
  $buf += (& $adb shell dumpsys usb | Select-String -Pattern "Accessory|accessory|CONFIGURED|Android Auto|ACCESSORY" | Select-Object -First 30 | Out-String)
  $buf += (& $adb logcat -d -t 60 -s Amni.AaWatch:I Amni.AoaProbe:I | Out-String)
  $buf | Set-Content $rep -Encoding UTF8
  Write-Host "wrote $rep"
  & $adb shell screencap -p /sdcard/amni_cap.png 2>$null
  & $adb pull /sdcard/amni_cap.png (Join-Path $outDir ("phone_" + (Get-Date -Format "HHmmss") + ".png")) 2>$null
  Start-Sleep 10
}
Write-Host "done $outDir"
