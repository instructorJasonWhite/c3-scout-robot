param(
    [string]$Serial,
    [string]$ApkPath,
    [string]$AdbPath
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $ApkPath) { $ApkPath = Join-Path $projectRoot 'artifacts\Scout-Control-debug.apk' }
if (-not (Test-Path -LiteralPath $ApkPath -PathType Leaf)) { throw "APK not found: $ApkPath. Run build-android.ps1 first." }
$ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
if (-not $AdbPath) {
    $adbCommand = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($adbCommand) { $AdbPath = $adbCommand.Source }
    else {
        foreach ($sdkPath in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
            (Join-Path $env:USERPROFILE 'tools\android-sdk'),
            (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
            if ($sdkPath) {
                $candidate = Join-Path $sdkPath 'platform-tools\adb.exe'
                if (Test-Path -LiteralPath $candidate -PathType Leaf) { $AdbPath = $candidate; break }
            }
        }
    }
}
if (-not $AdbPath -or -not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) { throw 'ADB was not found. Supply -AdbPath.' }
$deviceOutput = & $AdbPath devices -l
if ($LASTEXITCODE -ne 0) { throw 'ADB could not list connected devices.' }
$devices = @($deviceOutput | ForEach-Object {
    if ($_ -match '^(\S+)\s+(device|unauthorized|offline)\b(.*)$') {
        [pscustomobject]@{ Serial = $Matches[1]; State = $Matches[2]; Details = $Matches[3] }
    }
})
$physicalDevices = @($devices | Where-Object {
    $_.State -eq 'device' -and $_.Serial -notmatch '^emulator-|:|\._tcp\.?$'
} | Where-Object {
    # Windows ADB may omit the usb: detail even for a physical USB connection.
    $emulatorFlag = & $AdbPath -s $_.Serial shell getprop ro.kernel.qemu
    if ($LASTEXITCODE -ne 0) { throw "Could not identify connected device '$($_.Serial)'." }
    ($emulatorFlag -join '').Trim() -ne '1'
})
if ($Serial) {
    $selected = @($physicalDevices | Where-Object { $_.Serial -eq $Serial })
    if ($selected.Count -ne 1) { throw "Device '$Serial' is not an authorized physical USB device. Check USB debugging and its authorization prompt." }
} else {
    if ($physicalDevices.Count -ne 1) {
        throw "Expected one authorized physical USB device; found $($physicalDevices.Count). Connect and authorize the phone, or select one with -Serial."
    }
    $selected = $physicalDevices
}
$deviceSerial = $selected[0].Serial
Write-Host "Installing Scout Control on $deviceSerial"
& $AdbPath -s $deviceSerial install -r $ApkPath
if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }
$launchOutput = & $AdbPath -s $deviceSerial shell am start -W -n 'com.scout.control/.MainActivity'
$launchExitCode = $LASTEXITCODE
$launchOutput | Write-Host
if ($launchExitCode -ne 0 -or ($launchOutput -match 'Error:|Exception')) { throw 'Scout Control could not be launched.' }
Write-Host 'Scout Control installed and launched.'
