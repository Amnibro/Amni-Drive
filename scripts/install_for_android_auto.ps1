$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk" | Resolve-Path -ErrorAction SilentlyContinue
if (-not $apk) { $apk = "C:\Users\antho\Documents\ai\Amni-Drive\app\build\outputs\apk\debug\app-debug.apk" }
if (-not (Test-Path $adb)) { Write-Host "adb not found: $adb"; exit 1 }
if (-not (Test-Path $apk)) { Write-Host "APK not found: $apk — run gradle :app:assembleDebug first"; exit 1 }
Write-Host "Waiting for device (enable Wireless debugging / keep AA USB plugged)..."
& $adb start-server | Out-Null
$ok = $false
for ($i = 1; $i -le 60; $i++) {
  $list = & $adb devices 2>&1 | Out-String
  if ($list -match "\tdevice") { $ok = $true; break }
  Write-Host "  try $i — no device yet"
  Start-Sleep 2
}
if (-not $ok) { Write-Host "No device. On phone: Developer options → Wireless debugging → Pair/connect, or USB debugging."; exit 2 }
Write-Host "Device online. Reinstalling as Play Store installer (AA can see it)..."
& $adb uninstall com.amni.drive 2>$null
$r = & $adb install -r -g -i com.android.vending "$apk" 2>&1 | Out-String
Write-Host $r
if ($r -notmatch "Success") {
  Write-Host "Play-installer install failed; trying normal install + set-installer..."
  & $adb install -r -g "$apk" 2>&1 | Write-Host
  & $adb shell cmd package set-installer com.amni.drive com.android.vending 2>&1 | Write-Host
}
Write-Host "Installer tag:"
& $adb shell pm list packages -i 2>&1 | Select-String "amni.drive"
& $adb shell am force-stop com.google.android.projection.gearhead 2>$null
& $adb shell am start -a com.google.android.projection.gearhead.SETTINGS 2>$null
& $adb shell am start -n com.amni.drive/.MainActivity 2>$null
Write-Host ""
Write-Host "DONE. On phone Android Auto settings:"
Write-Host "  1) Profile → About (tap 10x) → ⋮ → Developer settings → Unknown sources ON"
Write-Host "  2) Unplug car 5s → plug back in → Apps grid → Amni Drive"
Write-Host "  3) If still missing: Settings → Apps → Android Auto → Storage → Clear cache (not data)"
