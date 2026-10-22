$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$msvc = 'C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Tools\MSVC\14.51.36231'
$out = Join-Path $project 'firmware/build/faces-host'
New-Item -ItemType Directory -Force $out | Out-Null
$test = Join-Path $project 'firmware/tests/faces-host'
$lib = Join-Path $project 'libraries/ScoutFaces/src'
$env:INCLUDE = Join-Path $msvc 'include'
& (Join-Path $msvc 'bin/Hostx64/x64/cl.exe') /nologo /std:c++17 /W4 /WX /GS- /Zl "/I$test" "/I$lib" (Join-Path $test 'timeline.cpp') (Join-Path $lib 'ScoutFaces.cpp') "/Fo$out\" "/Fe$out/timeline.exe" /link /nodefaultlib /entry:main /subsystem:console (Join-Path $project 'firmware/build/host/kernel32.lib')
if ($LASTEXITCODE -ne 0) { throw 'ScoutFaces host compile failed' }
& (Join-Path $out 'timeline.exe')
if ($LASTEXITCODE -ne 0) { throw "ScoutFaces timeline check failed at line $LASTEXITCODE" }
Write-Output 'ScoutFaces timeline checks passed: poses, cycle, hold, catch-up, rollover, invalid geometry and speed clamp.'
