@echo off
setlocal enabledelayedexpansion
echo === NEXUS CLIENT builder ===
set "JDK="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JDK=%%D"
if "%JDK%"=="" (
  echo Java 21 not found in C:\Program Files\Eclipse Adoptium\
  echo Install Temurin JDK 21 from https://adoptium.net/temurin/releases/?version=21 and run this again.
  pause & exit /b 1
)
set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%PATH%"
echo Using %JDK%
where gradle >nul 2>nul
if errorlevel 1 if not exist gradlew.bat (
  echo Gradle not found. Run:  winget install Gradle.Gradle   then close and reopen this window.
  pause & exit /b 1
)
if not exist gradlew.bat call gradle wrapper
call gradlew.bat build
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Copy the red error lines and send them to Claude.
  pause & exit /b 1
)
echo.
echo DONE. Your mod is: %cd%\build\libs\nexus-client-1.0.0.jar
start "" "%cd%\build\libs"
pause
