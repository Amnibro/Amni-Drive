# Research helper: release Google gearhead, start Amni AOA probe, capture permission/open.
# Does NOT root the phone. User may need to tap "OK" on USB accessory permission dialog.
param(
  [string]$Serial = ""
)
$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { $adb = "adb" }
function A([string]$args) {
  if ($Serial) { & $adb -s $Serial shell $args } else { & $adb shell $args }
}
Write-Host "== devices =="
& $adb devices -l
if (-not $Serial) {
  $Serial = (& $adb devices | Select-String "device$" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1)
}
Write-Host "Using serial: $Serial"
Write-Host "== USB before =="
& $adb -s $Serial shell "dumpsys usb" | Select-String "current_functions|model=|ACCESSORY" | Select-Object -First 12
Write-Host "== force-stop gearhead (research) =="
& $adb -s $Serial shell am force-stop com.google.android.projection.gearhead
Start-Sleep -Seconds 1
Write-Host "== start Amni AOA attach/probe =="
& $adb -s $Serial shell am force-stop com.amni.drive
& $adb -s $Serial shell am start -n com.amni.drive/.wire.AoaAttachActivity
Start-Sleep -Seconds 2
Write-Host "== wait for car re-AOA (unplug/replug if needed) =="
for ($i = 0; $i -lt 15; $i++) {
  Start-Sleep -Seconds 2
  $acc = & $adb -s $Serial shell "dumpsys usb" | Select-String "current_functions=ACCESSORY"
  $log = & $adb -s $Serial shell "run-as com.amni.drive tail -5 files/aoa_probe/probe-*.log 2>/dev/null"
  Write-Host "--- t=$i ---"
  Write-Host $acc
  Write-Host $log
  if ($log -match "OPENED|PERMISSION granted=true|RX ") { break }
}
Write-Host "== probe log tail =="
& $adb -s $Serial shell "run-as com.amni.drive sh -c 'tail -40 files/aoa_probe/probe-*.log 2>/dev/null'"
Write-Host "== done. If permission dialog appeared on phone, accept it and re-run. =="
