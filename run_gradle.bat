@echo off
set "JAVA_HOME=C:\Users\souri\tools\jdk17\jdk-17.0.12+7"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANDROID_HOME=C:\Users\souri\tools\android-sdk"
call "%~dp0gradlew.bat" %*
