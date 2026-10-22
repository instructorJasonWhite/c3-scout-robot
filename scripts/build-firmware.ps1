param(
    [string]$ArduinoCli = 'C:\Program Files\Arduino IDE\resources\app\lib\backend\resources\arduino-cli.exe',
    [string]$ArduinoData = 'C:\Users\instr\Documents\Codex\2026-10-02\https-ptcgwd-webex-com-ptcgwd-j\work\arduino-motor-test\data',
    [int]$Jobs = 2
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$build = Join-Path $project 'firmware\build\esp32c3'
$output = Join-Path $project 'firmware\output'
$config = Join-Path $project 'firmware\build\arduino-cli.yaml'
New-Item -ItemType Directory -Force $build,$output | Out-Null
& python (Join-Path $project 'scripts/build-web-control.py')
if ($LASTEXITCODE -ne 0) { throw 'Browser page generation failed' }
$data = $ArduinoData.Replace('\','/')
$local = (Join-Path $project 'firmware\build').Replace('\','/')
@"
directories:
  data: $data
  downloads: $local/downloads
  user: $local/user
"@ | Set-Content -LiteralPath $config -Encoding utf8
& $ArduinoCli compile --config-file $config --jobs $Jobs --fqbn 'esp32:esp32:esp32c3:CDCOnBoot=cdc' --library (Join-Path $project 'libraries\ScoutFaces') --build-path $build --output-dir $output (Join-Path $project 'firmware\ScoutDrive')
if ($LASTEXITCODE -ne 0) { throw "Firmware compile failed ($LASTEXITCODE)" }
