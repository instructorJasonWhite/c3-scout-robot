param(
    [Parameter(Mandatory=$true)][ValidatePattern('^COM[0-9]+$')][string]$Port,
    [switch]$RunOpenAndEspUsbConnected,
    [string]$ArduinoCli = 'C:\Program Files\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe'
)
$ErrorActionPreference = 'Stop'
if (-not $RunOpenAndEspUsbConnected) {
    throw 'Confirm the physical setup first: carrier RUN is OPEN and ESP USB is connected. Then provide -RunOpenAndEspUsbConnected. Unplug ESP USB before RUN ON.'
}
$project = Split-Path $PSScriptRoot -Parent
$config = Join-Path $project 'firmware\build\arduino-cli.yaml'
$output = Join-Path $project 'firmware\output'
if (-not (Test-Path -LiteralPath (Join-Path $output 'ScoutDrive.ino.bin'))) { throw 'Compile firmware first using scripts/build-firmware.ps1.' }
if (-not (Test-Path -LiteralPath $config)) { throw 'Run scripts/build-firmware.ps1 once to configure the installed toolchain.' }
& $ArduinoCli upload --config-file $config --fqbn 'esp32:esp32:esp32c3:CDCOnBoot=cdc' --port $Port --input-dir $output (Join-Path $project 'firmware\ScoutDrive')
if ($LASTEXITCODE -ne 0) { throw "Firmware upload failed ($LASTEXITCODE)" }
