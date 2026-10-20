$ErrorActionPreference = 'Stop'

# Self-contained Windows build helper. Requires Android Studio + Android SDK Platform 35.
$GradleVersion = '8.10.2'
$InstallRoot = Join-Path $env:LOCALAPPDATA 'CallPilot\gradle'
$GradleHome = Join-Path $InstallRoot "gradle-$GradleVersion"
$ZipPath = Join-Path $InstallRoot "gradle-$GradleVersion-bin.zip"
$GradleBat = Join-Path $GradleHome 'bin\gradle.bat'

if (-not (Test-Path $GradleBat)) {
    New-Item -ItemType Directory -Force -Path $InstallRoot | Out-Null
    if (-not (Test-Path $ZipPath)) {
        $Url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
        Write-Host "Downloading Gradle $GradleVersion..."
        Invoke-WebRequest -Uri $Url -OutFile $ZipPath
    }
    Write-Host 'Extracting Gradle...'
    Expand-Archive -Path $ZipPath -DestinationPath $InstallRoot -Force
}

Write-Host 'Building CallPilot debug APK...'
& $GradleBat --no-daemon --stacktrace assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE. Read the first '* What went wrong:' section above."
}

$Apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $Apk)) {
    throw "Gradle finished but APK was not found at: $Apk"
}
Write-Host "`nBuild successful. APK: $Apk" -ForegroundColor Green
