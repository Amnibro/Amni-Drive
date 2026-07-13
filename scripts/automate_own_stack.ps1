# Amni-Drive own-stack automation: claim AOA, projection, simulated taps, multi-display screenshots.
# Factory car SoC is not ADB-accessible. "Car screen" shots = AA/Amni virtual displays on the phone if present.
param(
  [string]$Serial = "",
  [int]$WaitSec = 120,
  [int]$Cycles = 3
)
$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Host "adb missing"; exit 1 }
$outDir = Join-Path $PSScriptRoot ("..\docs\captures\auto_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$script:Serial = $Serial
function Adb {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Rest)
  & $adb @Rest 2>&1
}
function NeedSerial {
  if ($script:Serial) {
    $dev = (Adb devices) | Out-String
    if ($dev -match [regex]::Escape($script:Serial)) { return $script:Serial }
  }
  foreach ($l in (Adb devices)) {
    if ($l -match "^(\S+)\s+device") {
      $script:Serial = $Matches[1]
      return $script:Serial
    }
  }
  return $null
}
function Shot([string]$tag) {
  $s = NeedSerial
  if (-not $s) { return }
  $png = Join-Path $outDir ($tag + ".png")
  $tmp = "/sdcard/amni_cap.png"
  Adb -s $s shell screencap -p $tmp | Out-Null
  Adb -s $s pull $tmp $png | Out-Null
  $len = 0
  if (Test-Path $png) { $len = (Get-Item $png).Length }
  Write-Host ("  shot " + $tag + " -> " + $png + " (" + $len + " bytes)")
}
function ShotAllDisplays([string]$prefix) {
  $s = NeedSerial
  if (-not $s) { return }
  Shot ($prefix + "_phone")
  $sf = (Adb -s $s shell dumpsys SurfaceFlinger --display-id) | Out-String
  $ids = [regex]::Matches($sf, "Display (\d+) \(HWC") | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
  $i = 0
  foreach ($id in $ids) {
    $png = Join-Path $outDir ($prefix + "_hwc" + $i + ".png")
    $tmp = "/sdcard/amni_hwc" + $i + ".png"
    Adb -s $s shell screencap -d $id -p $tmp | Out-Null
    Adb -s $s pull $tmp $png | Out-Null
    if ((Test-Path $png) -and ((Get-Item $png).Length -gt 100)) {
      Write-Host ("  shot HWC " + $id + " -> " + $png + " (" + (Get-Item $png).Length + " bytes)")
    }
    $i++
  }
}
function UiDump {
  $s = NeedSerial
  if (-not $s) { return $null }
  Adb -s $s shell uiautomator dump /sdcard/amni_ui.xml | Out-Null
  $local = Join-Path $outDir "ui_last.xml"
  Adb -s $s pull /sdcard/amni_ui.xml $local | Out-Null
  if (Test-Path $local) { return (Get-Content $local -Raw) }
  return $null
}
function TapAllowIfDialog {
  $xml = UiDump
  if (-not $xml) { return $false }
  $needles = @("OK", "Allow", "ALLOW", "Always allow", "ALWAYS ALLOW", "While using the app")
  foreach ($n in $needles) {
    $re = 'text="' + [regex]::Escape($n) + '"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $m = [regex]::Match($xml, $re)
    if (-not $m.Success) {
      $re2 = 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*text="' + [regex]::Escape($n) + '"'
      $m = [regex]::Match($xml, $re2)
    }
    if ($m.Success) {
      $x = [int]((([int]$m.Groups[1].Value) + ([int]$m.Groups[3].Value)) / 2)
      $y = [int]((([int]$m.Groups[2].Value) + ([int]$m.Groups[4].Value)) / 2)
      Write-Host ("  TAP " + $n + " @ " + $x + "," + $y)
      Adb -s (NeedSerial) shell input tap $x $y | Out-Null
      return $true
    }
  }
  return $false
}
function PullLogs([string]$tag) {
  $s = NeedSerial
  if (-not $s) { return }
  $log = Join-Path $outDir ($tag + ".log")
  Adb -s $s logcat -d -t 200 > $log
  $probe = Join-Path $outDir ($tag + "-probe.txt")
  Adb -s $s shell "run-as com.amni.drive sh -c 'tail -50 files/aoa_probe/probe-*.log 2>/dev/null; echo ---; tail -30 files/aoa_probe/projection.log 2>/dev/null'" > $probe
  Write-Host ("  logs -> " + $log)
}
Write-Host "=== Amni own-stack automation ==="
Write-Host ("captures: " + $outDir)
Write-Host ("Waiting up to " + $WaitSec + "s for device...")
$deadline = (Get-Date).AddSeconds($WaitSec)
while (-not (NeedSerial) -and (Get-Date) -lt $deadline) {
  Start-Sleep 2
  Adb start-server | Out-Null
}
$s = NeedSerial
if (-not $s) {
  Write-Host "NO DEVICE. Enable wireless debugging and re-run with -Serial host:port"
  Write-Host "Physical Kia dash cannot be ADB-captured. Phone + virtual displays only."
  exit 2
}
Write-Host ("Device: " + $s)
ShotAllDisplays "00_before"
for ($c = 1; $c -le $Cycles; $c++) {
  Write-Host ("=== cycle " + $c + "/" + $Cycles + " ===")
  Write-Host "force-stop classic AA (gearhead) + restart Amni process (fresh APK code)"
  Adb -s $s shell am force-stop com.google.android.projection.gearhead | Out-Null
  Adb -s $s shell am force-stop com.amni.drive | Out-Null
  Start-Sleep 1
  Write-Host "start Amni OWN_STACK (nav + projection + AOA via MainActivity)"
  Adb -s $s shell am start -n com.amni.drive/.MainActivity -a com.amni.drive.action.OWN_STACK | Out-Null
  Adb -s $s shell am start -n com.amni.drive/.wire.AoaAttachActivity | Out-Null
  Start-Sleep 3
  ShotAllDisplays ($c.ToString() + "_after_start")
  Write-Host "auto-tap permission dialogs (up to 8 tries)"
  for ($t = 0; $t -lt 8; $t++) {
    if (TapAllowIfDialog) {
      Start-Sleep 1
      ShotAllDisplays ($c.ToString() + "_after_tap" + $t)
    } else {
      Start-Sleep 1
    }
  }
  PullLogs ("cycle" + $c)
  $probePath = Join-Path $outDir ("cycle" + $c + "-probe.txt")
  $probeTxt = ""
  if (Test-Path $probePath) { $probeTxt = Get-Content $probePath -Raw }
  if ($probeTxt -match "OPENED|FULL UI projection|VirtualDisplay") {
    Write-Host "SUCCESS SIGNAL in probe logs"
    ShotAllDisplays ($c.ToString() + "_success")
    break
  }
  Write-Host "no OPENED yet - if car still on classic AA, unplug 3s and replug"
  Start-Sleep 3
}
Write-Host "=== summary ==="
Get-ChildItem $outDir | Format-Table Name, Length
Write-Host ("Open folder: " + $outDir)
Write-Host "NOTE: Physical Kia panel is not an ADB display. Secondary shots are phone + Android virtual displays."
exit 0
