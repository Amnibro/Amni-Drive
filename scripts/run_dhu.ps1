$ErrorActionPreference = "Continue"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = Join-Path $sdk "platform-tools\adb.exe"
$dhuDir = Join-Path $sdk "extras\google\auto"
$dhu = Join-Path $dhuDir "desktop-head-unit.exe"
if (-not (Test-Path $dhu)) {
  Write-Host "DHU not installed. Run Android Studio SDK Manager → SDK Tools → Android Auto Desktop Head Unit Emulator"
  Write-Host "Or: sdkmanager `"extras;google;auto`""
  exit 1
}
if (-not (Test-Path $adb)) { Write-Host "adb missing at $adb"; exit 1 }
Write-Host "=== Amni Drive · Desktop Head Unit ==="
Write-Host "1) On phone: Android Auto → ⋮ → Start head unit server (notification appears)"
Write-Host "2) Developer settings: Unknown sources ON, Application mode = Developer"
Write-Host "3) Phone unlocked, USB or wireless debugging connected to this PC"
Write-Host ""
$dev = (& $adb devices 2>&1 | Out-String)
if ($dev -notmatch "\tdevice") {
  Write-Host "No adb device. Connect wireless debugging / USB, then re-run."
  Write-Host $dev
  exit 2
}
Write-Host "Device online. Forwarding 5277 and launching DHU..."
& $adb forward tcp:5277 tcp:5277 2>&1 | Write-Host
Set-Location $dhuDir
Write-Host "Starting: $dhu"
& $dhu
