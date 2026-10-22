param([string]$MsvcRoot = 'C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Tools\MSVC\14.51.36231')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$out = Join-Path $project 'firmware\build\host'
New-Item -ItemType Directory -Force $out | Out-Null
$testSource = Join-Path $project 'firmware\tests\drive_core_test.cpp'
$testExe = Join-Path $out 'drive_core_test.exe'
$testObj = Join-Path $out 'drive_core_test.obj'
$kernelDef = Join-Path $out 'kernel32.def'
$kernelLib = Join-Path $out 'kernel32.lib'
@'
LIBRARY KERNEL32.dll
EXPORTS
GetStdHandle
WriteFile
ExitProcess
'@ | Set-Content -LiteralPath $kernelDef -Encoding ascii
$env:INCLUDE = Join-Path $MsvcRoot 'include'
if (-not (Test-Path -LiteralPath $kernelLib)) {
    & (Join-Path $MsvcRoot 'bin\Hostx64\x64\lib.exe') /nologo "/def:$kernelDef" /machine:x64 "/out:$kernelLib"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot build host import library' }
}
& (Join-Path $MsvcRoot 'bin\Hostx64\x64\cl.exe') /nologo /std:c++17 /W4 /WX /GS- /Zl $testSource "/Fo$testObj" "/Fe$testExe" /link /nodefaultlib /entry:main /subsystem:console $kernelLib
if ($LASTEXITCODE -ne 0) { throw "Firmware host compile failed ($LASTEXITCODE)" }
& $testExe
if ($LASTEXITCODE -ne 0) { throw "Firmware host tests failed ($LASTEXITCODE)" }
