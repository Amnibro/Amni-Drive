param([string]$Clicks = "", [string]$Drag = "", [string]$Out = "cap.png", [int]$WaitMs = 2000)
$ErrorActionPreference = "Continue"
Add-Type -AssemblyName System.Windows.Forms,System.Drawing
Add-Type -TypeDefinition @'
using System; using System.Runtime.InteropServices; using System.Windows.Forms; using System.Drawing;
public class DhuIo {
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern void mouse_event(int f, int dx, int dy, int d, int e);
  public struct RECT { public int L; public int T; public int R; public int B; }
  public static void Click(int x, int y) { Cursor.Position = new Point(x,y); mouse_event(0x0002,0,0,0,0); System.Threading.Thread.Sleep(40); mouse_event(0x0004,0,0,0,0); }
  public static void Drag(int x1, int y1, int x2, int y2) {
    Cursor.Position = new Point(x1,y1); mouse_event(0x0002,0,0,0,0); System.Threading.Thread.Sleep(120);
    int steps = 24;
    for (int i = 1; i <= steps; i++) { Cursor.Position = new Point(x1+(x2-x1)*i/steps, y1+(y2-y1)*i/steps); System.Threading.Thread.Sleep(14); }
    System.Threading.Thread.Sleep(120); mouse_event(0x0004,0,0,0,0);
  }
}
'@ -ReferencedAssemblies @("System.Windows.Forms","System.Drawing")
$p = Get-Process desktop-head-unit -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $p) { Write-Host "NO DHU"; exit 1 }
$r = New-Object DhuIo+RECT
[DhuIo]::SetForegroundWindow($p.MainWindowHandle) | Out-Null
Start-Sleep -Milliseconds 300
[DhuIo]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
if ($Drag) {
  $d = $Drag -split ','
  Write-Host "drag $Drag"
  [DhuIo]::Drag($r.L + [int]$d[0], $r.T + [int]$d[1], $r.L + [int]$d[2], $r.T + [int]$d[3])
  Start-Sleep -Milliseconds $WaitMs
}
foreach ($pair in ($Clicks -split ';' | Where-Object { $_ })) {
  $xy = $pair -split ','
  Write-Host "click $($xy[0]),$($xy[1])"
  [DhuIo]::Click($r.L + [int]$xy[0], $r.T + [int]$xy[1])
  Start-Sleep -Milliseconds $WaitMs
}
$w = $r.R - $r.L; $h = $r.B - $r.T
$bmp = New-Object Drawing.Bitmap $w, $h
$g = [Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($r.L, $r.T, 0, 0, (New-Object Drawing.Size $w, $h))
$bmp.Save($Out, [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Write-Host "saved $Out ($w x $h)"
