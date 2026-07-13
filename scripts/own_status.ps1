$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
function Get-Serial {
  $line = & $adb devices | Where-Object { $_ -match "\tdevice$" } | Select-Object -First 1
  if (-not $line) { return $null }
  return ($line -split "`t")[0].Trim()
}
$s = Get-Serial
if (-not $s) { Write-Host "NO DEVICE"; exit 2 }
Write-Host "serial=$s"
& $adb -s $s shell am startservice -n com.amni.drive/.wire.MeshDevBridgeService 2>$null
Start-Sleep 1
$token = & $adb -s $s shell "run-as com.amni.drive cat shared_prefs/mesh_bridge.xml 2>/dev/null" 2>&1
if ($token -match 'token">([^<]+)') { $t = $Matches[1] } else {
  $t = (& $adb -s $s shell "curl -s http://127.0.0.1:7931/token" 2>&1 | Out-String).Trim()
}
if (-not $t -or $t.Length -lt 8) {
  Write-Host "fetch token via logcat/prefs fallback"
  $t = (& $adb -s $s shell "dumpsys activity services com.amni.drive 2>/dev/null | head -c 200" 2>&1 | Out-String)
}
Write-Host "probing mesh on device..."
$own = & $adb -s $s shell "curl -s 'http://127.0.0.1:7931/own?token=$t'" 2>&1 | Out-String
if ($own -match "unauthorized" -or $own.Length -lt 20) {
  $tokLine = & $adb -s $s shell "curl -s http://127.0.0.1:7931/token" 2>&1 | Out-String
  $t = $tokLine.Trim()
  $own = & $adb -s $s shell "curl -s 'http://127.0.0.1:7931/own?token=$t'" 2>&1 | Out-String
}
Write-Host "TOKEN=$t"
Write-Host "OWN=$own"
$status = & $adb -s $s shell "curl -s 'http://127.0.0.1:7931/status?token=$t'" 2>&1 | Out-String
Write-Host "STATUS=$status"
Write-Host "---"
Write-Host "Tracks: RIDE (surface) | OWN (wire, Auth-3 wall) | LINK (open replacement)"
Write-Host "Docs: docs/research/14_gearhead_replacement_tracks.md"
