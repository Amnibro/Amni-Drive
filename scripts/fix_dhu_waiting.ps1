$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dhuDir = "$env:LOCALAPPDATA\Android\Sdk\extras\google\auto"
$dhu = Join-Path $dhuDir "desktop-head-unit.exe"
function Get-Serial {
  $line = & $adb devices | Where-Object { $_ -match "\tdevice$" -and $_ -notmatch "offline" } | Select-Object -First 1
  if (-not $line) { return $null }
  return ($line -split "`t")[0].Trim()
}
function Pull-Ui {
  $dump = "$env:TEMP\amni_aa_ui.xml"
  & $adb -s $script:s shell uiautomator dump /sdcard/aa_ui.xml 2>$null | Out-Null
  & $adb -s $script:s pull /sdcard/aa_ui.xml $dump 2>$null | Out-Null
  if (Test-Path $dump) { return (Get-Content $dump -Raw) }
  return ""
}
function Tap([string]$t) {
  $raw = Pull-Ui
  if ($raw -match "(?:text|content-desc)=`"$([regex]::Escape($t))`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"") {
    $cx = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $cy = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    & $adb -s $script:s shell input tap $cx $cy 2>$null
    Write-Host "  tap $t"
    return $true
  }
  return $false
}
function Tcp-Open {
  try {
    $c = New-Object System.Net.Sockets.TcpClient
    $ok = $c.BeginConnect("127.0.0.1", 5277, $null, $null).AsyncWaitHandle.WaitOne(2500, $false)
    $r = $ok -and $c.Connected
    $c.Close()
    return $r
  } catch { return $false }
}
Write-Host "=== Amni fix DHU Waiting for phone ==="
Write-Host "NEVER force-stop gearhead (kills wireless adb + breaks HU session)"
if (-not (Test-Path $adb)) { Write-Host "adb missing"; exit 1 }
if (-not (Test-Path $dhu)) { Write-Host "DHU missing"; exit 1 }
$script:s = Get-Serial
if (-not $script:s) { Write-Host "No device - unlock phone, wireless debugging on"; exit 2 }
Write-Host "serial=$script:s"
Get-Process desktop-head-unit -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 1
& $adb -s $script:s shell input keyevent KEYCODE_WAKEUP 2>$null
& $adb -s $script:s shell input keyevent KEYCODE_HOME 2>$null
Start-Sleep 1
& $adb -s $script:s shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity --activity-clear-task 2>$null
Start-Sleep 2
Write-Host "Stop head unit server (clean)"
if (Tap "More options") { Start-Sleep 0.6 }
Tap "Stop head unit server" | Out-Null
Start-Sleep 3
Write-Host "Start head unit server"
& $adb -s $script:s shell am start -n com.google.android.projection.gearhead/.companion.settings.DefaultSettingsActivity 2>$null
Start-Sleep 1.5
if (Tap "More options") { Start-Sleep 0.6 }
if (-not (Tap "Start head unit server")) { Write-Host "WARN: tap Start head unit server manually on phone" }
Start-Sleep 3
$listen = & $adb -s $script:s shell "toybox netstat -lnt 2>/dev/null | grep 5277 || echo NO" 2>&1 | Out-String
if ($listen -match "5277") { Write-Host "OK 5277 listening" } else { Write-Host "FAIL no 5277 - start HU server on phone" }
& $adb forward --remove-all 2>$null
& $adb -s $script:s forward tcp:5277 tcp:5277 | Out-Null
Write-Host "forward: $(& $adb forward --list)"
if (-not (Tcp-Open)) { Write-Host "FAIL PC cannot reach 5277"; exit 3 }
Write-Host "OK TCP 5277"
Write-Host "Launch DHU"
Start-Process -FilePath $dhu -ArgumentList @("-a", "localhost:5277", "-i", "touch") -WorkingDirectory $dhuDir
Start-Sleep 5
Write-Host "Restart DHU once (handshake trick)"
Get-Process desktop-head-unit -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep 2
& $adb -s $script:s forward tcp:5277 tcp:5277 2>$null | Out-Null
Start-Process -FilePath $dhu -ArgumentList @("-a", "localhost:5277", "-i", "touch") -WorkingDirectory $dhuDir
Start-Sleep 6
$p = Get-Process desktop-head-unit -ErrorAction SilentlyContinue
if ($p) { Write-Host "DHU UP pid=$($p.Id) - look for AA/Amni chrome, not Waiting for phone" } else { Write-Host "DHU failed" ; exit 4 }
Write-Host "Phone: keep unlocked, AA settings ok. Open Amni Drive on DHU if needed."
exit 0
