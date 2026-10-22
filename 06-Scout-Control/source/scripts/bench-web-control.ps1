# Temporary Wi-Fi switch for the RUN-open device test. Always restore the original profile.
param([string]$RestoreProfile = 'WC_White', [string]$TestScript = 'test-web-control-device.cjs')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$testResult = 1
try {
    netsh wlan connect name='C3-Scout-Test' | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Could not request Scout Wi-Fi connection' }
    $connected = $false
    for ($attempt=0; $attempt -lt 12; ++$attempt) {
        Start-Sleep -Seconds 1
        $probe = [Net.Sockets.TcpClient]::new()
        try {
            $task = $probe.ConnectAsync('192.168.4.1',80)
            if ($task.Wait(350) -and $probe.Connected) { $connected = $true; break }
        } catch {} finally { $probe.Dispose() }
    }
    if (!$connected) { throw 'Scout did not become reachable over Wi-Fi' }
    Start-Sleep -Seconds 2
    & 'C:/Users/instr/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node.exe' (Join-Path $PSScriptRoot $TestScript)
    $testResult = $LASTEXITCODE
} finally {
    netsh wlan connect name=$RestoreProfile | Out-Host
    Start-Sleep -Seconds 4
}
if ($testResult -ne 0) { throw "Device browser test failed ($testResult); original Wi-Fi restore requested" }
