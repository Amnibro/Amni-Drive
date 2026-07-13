$ErrorActionPreference = "Continue"
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$adb = Join-Path $sdk "platform-tools\adb.exe"
$dhuDir = Join-Path $sdk "extras\google\auto"
$forms = [Reflection.Assembly]::LoadWithPartialName("System.Windows.Forms")
$drawing = [Reflection.Assembly]::LoadWithPartialName("System.Drawing")
Add-Type -ReferencedAssemblies @($forms.Location, $drawing.Location) -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
using System.Windows.Forms;
using System.Drawing;
public class DhuMouse {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int n);
  [DllImport("user32.dll")] public static extern void mouse_event(int f, int dx, int dy, int d, int e);
  public struct RECT { public int Left; public int Top; public int Right; public int Bottom; }
  public static void Click(int x, int y) {
    Cursor.Position = new Point(x, y);
    mouse_event(0x0002, 0, 0, 0, 0);
    mouse_event(0x0004, 0, 0, 0, 0);
  }
}
"@
$s = (& $adb devices | Select-String "\tdevice" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1)
if (-not $s) { Write-Host "NO ADB DEVICE"; exit 1 }
Write-Host "serial=$s ver=$((& $adb -s $s shell dumpsys package com.amni.drive 2>&1 | Select-String 'versionName=' | Select-Object -First 1))"
function Ensure-HeadUnitServer {
  $listen = & $adb -s $s shell "ss -lnt 2>/dev/null | grep 5277" 2>&1 | Out-String
  if ($listen -match "5277") { Write-Host "5277 listening"; return }
  Write-Host "Starting head unit server..."
  & $adb -s $s shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity 2>$null
  Start-Sleep 1
  $dump = "$env:TEMP\dhu_ui.xml"
  function Dump {
    & $adb -s $s shell uiautomator dump /sdcard/aa_ui.xml 2>$null | Out-Null
    & $adb -s $s pull /sdcard/aa_ui.xml $dump 2>$null | Out-Null
    if (Test-Path $dump) { Get-Content $dump -Raw } else { "" }
  }
  function Tap([string]$t) {
    $raw = Dump
    if ($raw -match "text=`"$([regex]::Escape($t))`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"") {
      $cx = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
      $cy = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
      & $adb -s $s shell input tap $cx $cy 2>$null
      Write-Host "tap $t"; return $true
    }
    if ($raw -match "content-desc=`"$([regex]::Escape($t))`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"") {
      $cx = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
      $cy = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
      & $adb -s $s shell input tap $cx $cy 2>$null
      Write-Host "tap $t"; return $true
    }
    return $false
  }
  Tap "More options" | Out-Null
  Start-Sleep 0.8
  Tap "Start head unit server" | Out-Null
  Start-Sleep 2
}
Ensure-HeadUnitServer
& $adb -s $s forward --remove tcp:5277 2>$null
& $adb -s $s forward tcp:5277 tcp:5277
& $adb -s $s shell am startservice -n com.amni.drive/.aa.AmniMediaService 2>$null
& $adb -s $s shell input keyevent KEYCODE_WAKEUP 2>$null
Get-Process desktop-head-unit -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 1
Start-Process -FilePath "cmd.exe" -ArgumentList "/c start `"x`" /D `"$dhuDir`" desktop-head-unit.exe -a localhost:5277 -i touch" -WindowStyle Normal
Start-Sleep 8
$p = Get-Process desktop-head-unit -ErrorAction SilentlyContinue
if (-not $p) { Write-Host "DHU failed"; exit 2 }
for ($i = 0; $i -lt 15 -and $p.MainWindowHandle -eq [IntPtr]::Zero; $i++) { Start-Sleep 1; $p.Refresh() }
$hwnd = $p.MainWindowHandle
Write-Host "DHU pid=$($p.Id) hwnd=$hwnd"
if ($hwnd -eq [IntPtr]::Zero) { Write-Host "No window"; exit 3 }
[DhuMouse]::ShowWindow($hwnd, 9) | Out-Null
[DhuMouse]::SetForegroundWindow($hwnd) | Out-Null
Start-Sleep 1
$r = New-Object DhuMouse+RECT
[DhuMouse]::GetWindowRect($hwnd, [ref]$r) | Out-Null
$w = $r.Right - $r.Left
$h = $r.Bottom - $r.Top
Write-Host "window $($r.Left),$($r.Top) ${w}x${h}"
if ($w -lt 100) { Write-Host "bad rect"; exit 3 }
function Click-Rel([double]$fx, [double]$fy, [string]$tag) {
  $x = [int]($r.Left + $w * $fx)
  $y = [int]($r.Top + $h * $fy)
  Write-Host "click $tag @ $x,$y"
  [DhuMouse]::Click($x, $y)
  Start-Sleep 1.5
}
& $adb -s $s logcat -c 2>$null
# NEVER blind-click DHU dock/grid — opens Phone and can place real calls.
Write-Host "SAFE: no DHU mouse clicks (Phone risk). Amni via adb intent only."
& $adb -s $s shell input keyevent KEYCODE_ENDCALL 2>$null
& $adb -s $s shell am force-stop com.google.android.dialer 2>$null
& $adb -s $s shell am force-stop com.samsung.android.dialer 2>$null
& $adb -s $s shell am start -n com.amni.drive/.MainActivity 2>$null
& $adb -s $s shell am startservice -n com.amni.drive/.aa.AmniCarAppService 2>$null
Start-Sleep 6
Write-Host "=== CRASH CHECK ==="
$lines = & $adb -s $s logcat -d -t 300 2>&1
$bad = $lines | Select-String -Pattern "FATAL EXCEPTION|ViewTreeLifecycleOwner|Process: com.amni.drive|IllegalArgumentException|IllegalStateException"
if ($bad) {
  Write-Host "FAIL:"
  $bad | Select-Object -Last 20 | ForEach-Object { $_.Line }
  exit 4
}
Write-Host "PASS: no crash signatures after Search automation"
$surf = $lines | Select-String -Pattern "Amni.AaSurface|surface available|RIDE Presentation|fullUi"
if ($surf) { Write-Host "=== SURFACE ==="; $surf | Select-Object -Last 12 | ForEach-Object { $_.Line } }
exit 0
