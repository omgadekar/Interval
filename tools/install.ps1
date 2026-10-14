# Builds the debug APK and installs it on the connected watch (or emulator).
# Usage:  .\tools\install.ps1            (one device connected)
#         .\tools\install.ps1 -Device 192.168.1.42:5555
param([string]$Device)

$ErrorActionPreference = "Stop"
# Gradle needs JDK 17+; the Java on PATH may be older, so use Android Studio's bundled one.
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$project = Split-Path $PSScriptRoot -Parent

& "$project\gradlew.bat" -p $project :app:assembleDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = "$project\app\build\outputs\apk\debug\app-debug.apk"
if ($Device) { & $adb -s $Device install -r $apk } else { & $adb install -r $apk }