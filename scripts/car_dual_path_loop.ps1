# Amni-Drive dual-path car loop. Path A = Gearhead holds session (no AOA steal). Path B = own-stack when free.
param(
  [int]$Minutes = 45,
  [int]$IntervalSec = 8,
  [string]$Serial = ""
)
$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Host "adb missing"; exit 1 }
$root = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $root ("docs\captures\dual_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
function Adb { param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Rest); if ($script:Serial) { & $adb -s $script:Serial @Rest 2>&1 } else { & $adb @Rest 2>&1 } }
function NeedSerial {
  if ($script:Serial) { return $script:Serial }
  foreach ($l in (& $adb devices)) {
    if ($l -match "^(\S+)\s+device") { $script:Serial = $Matches[1]; return $script:Serial }
  }
  return $null
}
function Shot([string]$tag) {
  $s = NeedSerial; if (-not $s) { return }
  $png = Join-Path $outDir ($tag + ".png")
  Adb shell screencap -p /sdcard/amni_cap.png | Out-Null
  Adb pull /sdcard/amni_cap.png $png | Out-Null
  if (Test-Path $png) { Write-Host ("  shot " + $tag + " " + (Get-Item $png).Length) }
}
function Report([string]$tag) {
  $s = NeedSerial; if (-not $s) { return }
  $rep = Join-Path $outDir ($tag + "_report.txt")
  $lines = @()
  $lines += "=== $tag $(Get-Date -Format o) ==="
  $lines += "devices: " + ((Adb devices) | Out-String)
  $lines += "model: " + ((Adb shell getprop ro.product.model) | Out-String).Trim()
  $lines += "pkg: " + ((Adb shell dumpsys package com.amni.drive | Select-String "versionName|versionCode" | Select-Object -First 4 | Out-String)
  $lines += "procs:"
  $lines += (Adb shell "ps -A | grep -iE 'gearhead|amni.drive|projection'" | Out-String)
  $lines += "usb:"
  $lines += (Adb shell dumpsys usb | Select-String -Pattern "Accessory|accessory|CONFIGURED|Android Auto|USB_STATE|mCurrentFunctions" | Select-Object -First 40 | Out-String)
  $lines += "watch log:"
  $lines += (Adb shell "run-as com.amni.drive sh -c 'tail -40 files/aoa_probe/watch-*.log 2>/dev/null'" | Out-String)
  $lines += "probe log:"
  $lines += (Adb shell "run-as com.amni.drive sh -c 'tail -40 files/aoa_probe/probe-*.log 2>/dev/null'" | Out-String)
  $lines += "logcat AA:"
  $lines += (Adb logcat -d -t 120 | Select-String -Pattern "Amni\.|AoaProbe|AaWatch|gearhead|AndroidAuto|AHU|CarApp|Surface" | Select-Object -Last 40 | Out-String)
  $lines | Set-Content -Path $rep -Encoding UTF8
  Write-Host ("  report " + $rep)
}
Write-Host "=== dual-path car loop ==="
Write-Host "captures: $outDir"
if (-not (NeedSerial)) { Write-Host "no device"; exit 1 }
Write-Host "serial=$script:Serial"
Adb logcat -c | Out-Null
Adb shell am start -n com.amni.drive/.MainActivity | Out-Null
Start-Sleep -Seconds 2
Adb shell am startservice -n com.amni.drive/.wire.AaSessionWatchService | Out-Null
Report "00_start"
Shot "00_start"
$end = (Get-Date).AddMinutes($Minutes)
$n = 0
$sawAccessory = $false
$sawGearhead = $false
while ((Get-Date) -lt $end) {
  $n++
  $tag = ("{0:D3}" -f $n)
  Write-Host ("--- cycle $tag ---")
  $ps = (Adb shell "ps -A" | Out-String)
  if ($ps -match "gearhead") { $sawGearhead = $true }
  $usb = (Adb shell dumpsys usb | Out-String)
  if ($usb -match "ACCESSORY=START" -or $usb -match "UsbAccessory" -or $usb -match "Android Auto") {
    if (-not $sawAccessory) {
      $sawAccessory = $true
      Write-Host "  ACCESSORY/AA signal"
      Report ($tag + "_acc")
      Shot ($tag + "_acc")
    }
  }
  if ($n % 3 -eq 0) {
    Adb shell am startservice -a com.amni.drive.wire.WATCH_TICK -n com.amni.drive/.wire.AaSessionWatchService | Out-Null
    Report $tag
    Shot $tag
  }
  if ($sawGearhead -and $sawAccessory) {
    Write-Host "  Path A conditions: gearhead + accessory seen — keep RIDE, no AOA claim"
  }
  Start-Sleep -Seconds $IntervalSec
}
Report "zz_end"
Shot "zz_end"
Write-Host "done sawAccessory=$sawAccessory sawGearhead=$sawGearhead"
Write-Host $outDir
