$ErrorActionPreference = 'Stop'
$appRoot = $PSScriptRoot
$classes = Join-Path $appRoot 'build-host'
New-Item -ItemType Directory -Force $classes | Out-Null
& javac -d $classes (Join-Path $appRoot 'app/src/main/java/com/scout/control/Mixer.java') (Join-Path $appRoot 'app/src/main/java/com/scout/control/SessionState.java') (Join-Path $appRoot 'app/src/main/java/com/scout/control/RobotHttp.java') (Join-Path $appRoot 'app/src/main/java/com/scout/control/DriveDatagram.java') (Join-Path $appRoot 'tests/ControlTests.java') (Join-Path $appRoot 'tests/SessionTests.java') (Join-Path $appRoot 'tests/HttpTests.java') (Join-Path $appRoot 'tests/DatagramTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Host test compilation failed' }
& java -cp $classes com.scout.control.ControlTests
if ($LASTEXITCODE -ne 0) { throw 'Mixer tests failed' }
& java -cp $classes com.scout.control.SessionTests
if ($LASTEXITCODE -ne 0) { throw 'Session tests failed' }
& java -cp $classes com.scout.control.HttpTests
if ($LASTEXITCODE -ne 0) { throw 'HTTP tests failed' }
& java -cp $classes com.scout.control.DatagramTests
if ($LASTEXITCODE -ne 0) { throw 'Packet tests failed' }
& javac -d $classes (Join-Path $appRoot 'app/src/main/java/com/scout/control/FaceSelection.java') (Join-Path $appRoot 'tests/FaceTests.java')
if ($LASTEXITCODE -ne 0) { throw 'Face selection test compilation failed' }
& java -cp $classes com.scout.control.FaceTests
if ($LASTEXITCODE -ne 0) { throw 'Face selection tests failed' }
