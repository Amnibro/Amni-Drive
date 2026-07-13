$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dhuDir = "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto"
$dhu = Join-Path $dhuDir "desktop-head-unit.exe"
$capDir = Join-Path $PSScriptRoot "..\docs\captures"
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $capDir "verify_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
function Get-Serial {
  $line = & $adb devices | Where-Object { $_ -match "\tdevice$" -and $_ -notmatch "offline" } | Select-Object -First 1
  if (-not $line) { return $null }
  return ($line -split "`t")[0].Trim()
}
function Pull-Ui([string]$s) {
  $dump = Join-Path $env:TEMP "amni_dhu_ui.xml"
  & $adb -s $s shell uiautomator dump /sdcard/aa_ui.xml 2>$null | Out-Null
  & $adb -s $s pull /sdcard/aa_ui.xml $dump 2>$null | Out-Null
  if (Test-Path $dump) { return (Get-Content $dump -Raw) }
  return ""
}
function Tap([string]$s, [string]$t) {
  $raw = Pull-Ui $s
  if ($raw -match "(?:text|content-desc)=`"$([regex]::Escape($t))`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"") {
    $cx = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $cy = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    & $adb -s $s shell input tap $cx $cy 2>$null
    Write-Host "  tap $t"
    return $true
  }
  return $false
}
function Cap-Dhu([string]$name) {
  $forms = [Reflection.Assembly]::LoadWithPartialName("System.Windows.Forms")
  $drawing = [Reflection.Assembly]::LoadWithPartialName("System.Drawing")
  if (-not ("DhuCap" -as [type])) {
    Add-Type -ReferencedAssemblies @($forms.Location, $drawing.Location) -TypeDefinition @"
using System; using System.Runtime.InteropServices; using System.Windows.Forms; using System.Drawing;
public class DhuCap {
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern void mouse_event(int f, int dx, int dy, int d, int e);
  public struct RECT { public int L; public int T; public int R; public int B; }
  public static void Click(int x, int y) { Cursor.Position = new Point(x,y); mouse_event(0x0002,0,0,0,0); mouse_event(0x0004,0,0,0,0); }
}
"@
  }
  $p = Get-Process desktop-head-unit -ErrorAction SilentlyContinue | Select-Object -First 1
  if (-not $p) { Write-Host "no DHU process"; return $null }
  $r = New-Object DhuCap+RECT
  [DhuCap]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
  Start-Sleep -Milliseconds 250
  [DhuCap]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
  $w = $r.R - $r.L; $h = $r.B - $r.T
  if ($w -lt 80) { return $null }
  $bmp = New-Object Drawing.Bitmap $w, $h
  $g = [Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($r.L, $r.T, 0, 0, (New-Object Drawing.Size $w, $h))
  $path = Join-Path $outDir $name
  $bmp.Save($path, [Drawing.Imaging.ImageFormat]::Png)
  $g.Dispose(); $bmp.Dispose()
  $img = [Drawing.Bitmap]::FromFile($path)
  $sum = 0.0; $n = 0
  for ($x = 0; $x -lt $img.Width; $x += 18) {
    for ($y = 0; $y -lt $img.Height; $y += 18) {
      $c = $img.GetPixel($x, $y); $sum += ($c.R + $c.G + $c.B) / 3.0; $n++
    }
  }
  $img.Dispose()
  $bright = [math]::Round($sum / [math]::Max($n, 1), 1)
  Write-Host "  cap $name bright=$bright ${w}x${h}"
  return @{ path = $path; L = $r.L; T = $r.T; W = $w; H = $h; bright = $bright }
}
function Click-Rel($info, [double]$fx, [double]$fy, [string]$tag) {
  $x = [int]($info.L + $info.W * $fx)
  $y = [int]($info.T + $info.H * $fy)
  Write-Host "  click $tag @$x,$y"
  [DhuCap]::Click($x, $y)
  Start-Sleep 1.4
}
Write-Host "=== Amni RIDE DHU verify $stamp ==="
if (-not (Test-Path $adb)) { Write-Host "adb missing"; exit 1 }
if (-not (Test-Path $dhu)) { Write-Host "DHU missing"; exit 1 }
$s = Get-Serial
if (-not $s) { Write-Host "NO DEVICE"; exit 2 }
Write-Host "serial=$s"
$apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
  Write-Host "install APK..."
  & $adb -s $s install -r -g -i com.android.vending $apk 2>&1 | Out-Host
}
$ver = & $adb -s $s shell dumpsys package com.amni.drive 2>&1 | Select-String "versionName=|versionCode=" | Select-Object -First 4
$ver | ForEach-Object { Write-Host "  $_" }
& $adb -s $s shell input keyevent KEYCODE_WAKEUP 2>$null
& $adb -s $s shell am force-stop com.amni.drive 2>$null
Start-Sleep 1
Write-Host "head unit server restart (never force-stop gearhead)"
& $adb -s $s shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity --activity-clear-task 2>$null
Start-Sleep 2
if (Tap $s "More options") { Start-Sleep 0.5 }
if (Tap $s "Stop head unit server") { Start-Sleep 2 }
& $adb -s $s shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity 2>$null
Start-Sleep 1.2
if (Tap $s "More options") { Start-Sleep 0.5 }
Tap $s "Start head unit server" | Out-Null
Start-Sleep 2
$listen = & $adb -s $s shell "toybox netstat -lnt 2>/dev/null | grep 5277 || echo NO" 2>&1 | Out-String
Write-Host "5277: $($listen.Trim())"
& $adb forward --remove-all 2>$null
& $adb -s $s forward tcp:5277 tcp:5277 | Out-Null
Write-Host "fwd: $(& $adb forward --list)"
Get-Process desktop-head-unit -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 1
Start-Process -FilePath $dhu -ArgumentList @("-a", "localhost:5277", "-i", "touch") -WorkingDirectory $dhuDir
Start-Sleep 5
Get-Process desktop-head-unit -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 1
& $adb -s $s forward tcp:5277 tcp:5277 2>$null | Out-Null
Start-Process -FilePath $dhu -ArgumentList @("-a", "localhost:5277", "-i", "touch") -WorkingDirectory $dhuDir
Start-Sleep 8
& $adb -s $s logcat -c 2>$null
$i = Cap-Dhu "01_idle.png"
if (-not $i) { Write-Host "FAIL no DHU window"; exit 3 }
# NEVER click AA dock/grid - Phone/Spotify live there and we have already opened real calls by accident.
# Amni only: package intents on the phone (AA host may surface the nav app when session is live).
Write-Host "SAFE: no DHU mouse clicks (dock/grid banned)"
& $adb -s $s shell am force-stop com.google.android.dialer 2>$null
& $adb -s $s shell am force-stop com.samsung.android.dialer 2>$null
& $adb -s $s shell am force-stop com.android.server.telecom 2>$null
& $adb -s $s shell input keyevent KEYCODE_ENDCALL 2>$null
& $adb -s $s shell am start -n com.amni.drive/.MainActivity -a android.intent.action.MAIN -c android.intent.category.LAUNCHER 2>$null | Out-Null
& $adb -s $s shell am startservice -n com.amni.drive/.aa.AmniCarAppService 2>$null | Out-Null
Start-Sleep 3
$i = Cap-Dhu "02_after_amni_intent.png"
if (-not $i) { $i = Cap-Dhu "02_after_amni_intent.png" }
$logs = & $adb -s $s logcat -d -t 150 2>&1
$crash = $logs | Select-String "FATAL EXCEPTION|Action strip for this template|Process: com.amni.drive"
$surf = $logs | Select-String "Amni.AaSurface|fullUi=true|Presentation shown|RIDE"
$report = Join-Path $outDir "report.txt"
$bright = if ($i) { $i.bright } else { 0 }
@(
  "stamp=$stamp",
  "serial=$s",
  "ver=$($ver -join ' | ')",
  "bright_final=$bright",
  "crash_lines=$($crash.Count)",
  "surface_lines=$($surf.Count)",
  "clicks=NONE (dock/grid banned - Phone risk)"
) | Set-Content $report
if ($crash) { $crash | ForEach-Object { $_.Line } | Add-Content $report }
if ($surf) { $surf | ForEach-Object { $_.Line } | Add-Content $report }
Write-Host "=== RESULT ==="
Write-Host "out=$outDir"
Write-Host "bright=$bright crash=$($crash.Count) surface=$($surf.Count) clicks=NONE"
if ($bright -lt 30) { Write-Host "WARN dark/waiting screen - open Amni on DHU manually"; exit 4 }
if ($crash) { Write-Host "FAIL crash signatures"; exit 5 }
Write-Host "PASS (no blind dock clicks; screenshots + logs only)"
exit 0
