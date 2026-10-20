@echo off
set "JAVA_HOME=C:\Users\souri\tools\jdk17\jdk-17.0.12+7"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANDROID_HOME=C:\Users\souri\tools\android-sdk"

echo Accepting licenses...
(echo y & echo y & echo y & echo y & echo y & echo y & echo y & echo y) | C:\Users\souri\tools\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=C:\Users\souri\tools\android-sdk --licenses

echo Installing platforms;android-34 and build-tools;34.0.0...
(echo y) | C:\Users\souri\tools\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=C:\Users\souri\tools\android-sdk "platforms;android-34" "build-tools;34.0.0"

echo Done!
