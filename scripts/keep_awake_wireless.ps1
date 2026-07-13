# Best-effort: keep screen on while plugged + stabilize WiFi for wireless ADB.
# Cannot fully prevent wireless debugging port changes on USB AOA re-enum.
param([string]$Serial = "")
$ErrorActionPreference = "Continue"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
function NeedSerial {
  if ($Serial) { return $Serial }
  $mdns = (& $adb mdns services 2>&1 | Out-String)
  if ($mdns -match "(\d+\.\d+\.\d+\.\d+):(\d+)") {
    & $adb connect ($Matches[1] + ":" + $Matches[2]) 2>&1 | Out-Null
    Start-Sleep 1
  }
  foreach ($l in (& $adb devices 2>&1)) {
    if ($l -match "^(\S+)\s+device") { return $Matches[1] }
  }
  return $null
}
$s = NeedSerial
if (-not $s) { Write-Host "NO DEVICE"; exit 2 }
Write-Host "device $s"
& $adb -s $s shell settings put global stay_on_while_plugged_in 7
& $adb -s $s shell svc power stayon true
& $adb -s $s shell settings put system screen_off_timeout 2147483647
& $adb -s $s shell settings put global adb_enabled 1
& $adb -s $s shell settings put global wifi_sleep_policy 2
& $adb -s $s shell settings put global wifi_on 1
& $adb -s $s shell dumpsys deviceidle disable
& $adb -s $s shell input keyevent KEYCODE_WAKEUP
& $adb -s $s shell wm dismiss-keyguard
Write-Host "stay_on_while_plugged_in=" (& $adb -s $s shell settings get global stay_on_while_plugged_in)
Write-Host "screen_off_timeout=" (& $adb -s $s shell settings get system screen_off_timeout)
Write-Host "adb_wifi_enabled=" (& $adb -s $s shell settings get global adb_wifi_enabled)
Write-Host "done"
exit 0
