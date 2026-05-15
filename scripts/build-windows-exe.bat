@echo off
setlocal EnableExtensions
REM Build SYSCO.exe (Windows app-image with bundled JRE).
REM Prerequisites:
REM   1. JDK 17+ on PATH (same major version as pom.xml). JAVA_HOME should point to the JDK.
REM   2. Maven
REM   3. OpenJFX *jmods* (not the SDK) for Windows, same version as pom (see javafx.version in pom.xml):
REM      https://openjfx.io/ -> download the "jmods" build for Windows, extract so you have:
REM        %JAVAFX_JMODS%\javafx.base.jmod
REM      If you only have javafx.jar files under "lib", you downloaded the SDK — get the jmods ZIP instead.
REM
REM Usage:
REM   set JAVAFX_JMODS=C:\javafx\javafx-jmods-17.0.18
REM   scripts\build-windows-exe.bat

if "%JAVAFX_JMODS%"=="" (
  echo ERROR: Set JAVAFX_JMODS to the directory containing javafx.base.jmod ^(OpenJFX jmods for Windows^).
  exit /b 1
)

REM Accept any layout: Maven resolves nested folders ^(see pom windows-jprofile^).
dir /s /b "%JAVAFX_JMODS%\javafx.base.jmod" 2>nul | findstr /r "." >nul
if errorlevel 1 (
  echo ERROR: No javafx.base.jmod under %JAVAFX_JMODS% ^(recursive search^).
  echo Download Windows *jmods* ZIP from https://openjfx.io/ ^(same major.minor as javafx.version in pom.xml^), extract, then point JAVAFX_JMODS at that folder.
  exit /b 1
)

pushd "%~dp0\.."
call mvn -Pwindows-jpackage "-Djavafx.jmods.dir=%JAVAFX_JMODS%" clean package
set ERR=%ERRORLEVEL%
popd
if not "%ERR%"=="0" exit /b %ERR%

echo.
echo Done. Run:  target\dist\SYSCO\SYSCO.exe
echo Zip folder target\dist\SYSCO for distribution ^(includes runtime and app^).
exit /b 0
