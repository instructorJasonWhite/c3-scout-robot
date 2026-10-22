& (Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts\test-firmware.ps1') @args
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
