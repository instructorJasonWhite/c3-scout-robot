param(
    [string]$SdkPath,
    [string]$JdkPath,
    [string]$GradlePath,
    [string]$HostTestScript
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $projectRoot 'android'

function Find-Directory([string[]]$Candidates, [string]$Description) {
    foreach ($candidate in $Candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Container)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }
    throw "$Description was not found. Supply its path using the corresponding script parameter."
}

$SdkPath = Find-Directory @($SdkPath, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
    (Join-Path $env:USERPROFILE 'tools\android-sdk'),
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk')) 'Android SDK'
if (-not $JdkPath) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    $javaHomeFromPath = if ($javaCommand) { Split-Path -Parent (Split-Path -Parent $javaCommand.Source) }
    $JdkPath = Find-Directory @($env:JAVA_HOME, $javaHomeFromPath,
        (Join-Path $env:USERPROFILE 'tools\jdk-21.0.12.1+1')) 'JDK'
} else {
    $JdkPath = Find-Directory @($JdkPath) 'JDK'
}
if (-not (Test-Path -LiteralPath (Join-Path $JdkPath 'bin\javac.exe'))) {
    throw "JDK must include bin\javac.exe: $JdkPath"
}
if (-not $GradlePath) {
    if ($env:GRADLE_HOME) { $GradlePath = Join-Path $env:GRADLE_HOME 'bin\gradle.bat' }
    elseif (Test-Path -LiteralPath (Join-Path $androidRoot 'gradlew.bat')) {
        $GradlePath = Join-Path $androidRoot 'gradlew.bat'
    } else {
        $gradleCommand = Get-Command gradle.bat -ErrorAction SilentlyContinue
        if ($gradleCommand) { $GradlePath = $gradleCommand.Source }
        else { $GradlePath = Join-Path $env:USERPROFILE 'tools\gradle-8.11.1\bin\gradle.bat' }
    }
}
if (Test-Path -LiteralPath $GradlePath -PathType Container) { $GradlePath = Join-Path $GradlePath 'bin\gradle.bat' }
if (-not (Test-Path -LiteralPath $GradlePath -PathType Leaf)) { throw "Gradle not found: $GradlePath" }
if (-not (Test-Path -LiteralPath (Join-Path $androidRoot 'settings.gradle')) -and
    -not (Test-Path -LiteralPath (Join-Path $androidRoot 'settings.gradle.kts'))) {
    throw "Android project settings are missing from $androidRoot"
}

$oldJavaHome = $env:JAVA_HOME
$oldAndroidHome = $env:ANDROID_HOME
$oldSdkRoot = $env:ANDROID_SDK_ROOT
$oldPath = $env:PATH
try {
    $env:JAVA_HOME = $JdkPath
    $env:ANDROID_HOME = $SdkPath
    $env:ANDROID_SDK_ROOT = $SdkPath
    $env:PATH = "$(Join-Path $JdkPath 'bin');$oldPath"
    if (-not $HostTestScript) {
        foreach ($candidate in @('scripts\test-android.ps1', 'android\test-host.ps1', 'android\tests\run-tests.ps1')) {
            $testPath = Join-Path $projectRoot $candidate
            if (Test-Path -LiteralPath $testPath -PathType Leaf) { $HostTestScript = $testPath; break }
        }
    }
    if ($HostTestScript) {
        $testPath = (Resolve-Path -LiteralPath $HostTestScript).Path
        $shellPath = (Get-Process -Id $PID).Path
        & $shellPath -NoProfile -ExecutionPolicy Bypass -File $testPath
        if ($LASTEXITCODE -ne 0) { throw "Android host tests failed with exit code $LASTEXITCODE" }
    } else { Write-Warning 'No Android host test script was found.' }

    & $GradlePath -p $androidRoot --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE" }
    $builtApk = Join-Path $androidRoot 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $builtApk)) { throw "Expected debug APK missing: $builtApk" }
    $signer = Get-ChildItem -LiteralPath (Join-Path $SdkPath 'build-tools') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending |
        ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
        Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if (-not $signer) { throw 'Android SDK apksigner.bat was not found.' }
    & $signer verify --verbose --print-certs $builtApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    $artifactDirectory = Join-Path $projectRoot 'artifacts'
    New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
    $artifactApk = Join-Path $artifactDirectory 'Scout-Control-debug.apk'
    Copy-Item -LiteralPath $builtApk -Destination $artifactApk -Force
    Write-Host "Verified APK: $artifactApk"
    Get-FileHash -LiteralPath $artifactApk -Algorithm SHA256
} finally {
    $env:JAVA_HOME = $oldJavaHome
    $env:ANDROID_HOME = $oldAndroidHome
    $env:ANDROID_SDK_ROOT = $oldSdkRoot
    $env:PATH = $oldPath
}
