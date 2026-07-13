# Soft "unplug/replug" from phone side (no physical cable pull).
# Uses: enableUsbDataSignal false/true, resetUsbGadget, optional resetUsbPort.
# Wireless ADB may drop briefly — script re-finds device via mdns.
param([int]$WaitAfterSec = 10)
$ErrorActionPreference = "Continue"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
function NeedSerial {
  foreach ($l in (& $adb devices 2>&1)) {
    if ($l -match "^(\S+)\s+device") { return $Matches[1] }
  }
  $mdns = (& $adb mdns services 2>&1 | Out-String)
  if ($mdns -match "(\d+\.\d+\.\d+\.\d+):(\d+)") {
    & $adb connect ($Matches[1] + ":" + $Matches[2]) 2>&1 | Out-Null
    Start-Sleep 2
  }
  foreach ($l in (& $adb devices 2>&1)) {
    if ($l -match "^(\S+)\s+device") { return $Matches[1] }
  }
  return $null
}
$s = NeedSerial
if (-not $s) { Write-Host "NO DEVICE"; exit 2 }
Write-Host "device $s"
Write-Host "arm Amni probe, pause gearhead"
& $adb -s $s shell am force-stop com.google.android.projection.gearhead 2>&1 | Out-Null
& $adb -s $s shell am start -n com.amni.drive/.wire.AoaAttachActivity 2>&1 | Out-Null
Write-Host "USB data OFF (soft unplug data)"
& $adb -s $s shell svc usb enableUsbDataSignal false 2>&1
Start-Sleep 2
Write-Host "USB data ON"
& $adb -s $s shell svc usb enableUsbDataSignal true 2>&1
Start-Sleep 1
Write-Host "resetUsbGadget"
& $adb -s $s shell svc usb resetUsbGadget 2>&1
Start-Sleep 2
Write-Host "resetUsbPort (may drop adb briefly)"
& $adb -s $s shell svc usb resetUsbPort 2>&1
$deadline = (Get-Date).AddSeconds($WaitAfterSec + 20)
$s2 = $null
while ((Get-Date) -lt $deadline) {
  $s2 = NeedSerial
  if ($s2) { break }
  Start-Sleep 2
}
if (-not $s2) { Write-Host "lost ADB after bounce"; exit 3 }
Write-Host "back as $s2"
& $adb -s $s2 shell dumpsys usb 2>&1 | Select-String "current_functions=|kernel_state|usb_data_status|current_accessory" | Select-Object -First 12
& $adb -s $s2 shell "tail -15 /sdcard/Android/data/com.amni.drive/files/aoa_probe/probe-*.log 2>&1"
Write-Host "done"
exit 0
