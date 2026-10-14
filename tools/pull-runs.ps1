# Copies recorded runs (.tcx, .gpx, .json, raw .csv) from the watch into .\runs
# Usage:  .\tools\pull-runs.ps1            (one device connected)
#         .\tools\pull-runs.ps1 -Device 192.168.1.42:5555
param([string]$Device)

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$dest = Join-Path (Split-Path $PSScriptRoot -Parent) "runs"
New-Item -ItemType Directory -Force $dest | Out-Null

$src = "/sdcard/Android/data/com.om.int3r/files/runs/."
if ($Device) { & $adb -s $Device pull $src $dest } else { & $adb pull $src $dest }
Get-ChildItem $dest -Filter *.tcx | Sort-Object LastWriteTime -Descending | Select-Object -First 10 Name, LastWriteTime