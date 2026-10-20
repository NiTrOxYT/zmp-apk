$licDir = "C:\Users\souri\tools\android-sdk\licenses"
if (-not (Test-Path $licDir)) {
    New-Item -ItemType Directory -Force -Path $licDir | Out-Null
}
$hashes = @"
24333f8a63b6825ea9c5514f83c2829b004d1fee
8933bad161af4178b1185d1a37fbf41ea5269c55
d56f5187479451eabf01fb78af6dfcb131a6481e
"@
Set-Content -Path "$licDir\android-sdk-license" -Value $hashes -Encoding Ascii

$env:JAVA_HOME = "C:\Users\souri\tools\jdk17\jdk-17.0.12+7"
$env:ANDROID_HOME = "C:\Users\souri\tools\android-sdk"
$sdkManager = "C:\Users\souri\tools\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat"

& $sdkManager --sdk_root="C:\Users\souri\tools\android-sdk" "platforms;android-34" "build-tools;34.0.0"
Write-Host "SDK Installation Completed!"
