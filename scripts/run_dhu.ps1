$ErrorActionPreference = "Continue"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = Join-Path $sdk "platform-tools\adb.exe"
$dhuDir = Join-Path $sdk "extras\google\auto"
$dhu = Join-Path $dhuDir "desktop-head-unit.exe"
if (-not (Test-Path $dhu)) {
  Write-Host "Install SDK package: extras;google;auto (Android Auto Desktop Head Unit Emulator)"
  exit 1
}
$devs = & $adb devices 2>&1 | Out-String
if ($devs -notmatch "\tdevice") {
  Write-Host "No adb device. Connect wireless debugging first."
  Write-Host $devs
  exit 2
}
Write-Host ""
Write-Host "=== Amni Drive · Desktop Head Unit (fake car) ==="
Write-Host "On PHONE first:"
Write-Host "  Android Auto settings → tap Version 10x → Developer mode"
Write-Host "  Developer settings → Unknown sources ON"
Write-Host "  Developer settings → Start head unit server (keep notification)"
Write-Host "  Leave phone unlocked"
Write-Host ""
Write-Host "Then this script: adb forward 5277 + launch DHU"
Write-Host ""
& $adb forward --remove tcp:5277 2>$null
& $adb forward tcp:5277 tcp:5277
Write-Host "Forward list: $(& $adb forward --list)"
& $adb shell am startservice -n com.amni.drive/.aa.AmniMediaService 2>$null
Write-Host "Starting DHU window..."
Set-Location $dhuDir
& $dhu -a localhost:5277
