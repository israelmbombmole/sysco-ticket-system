@echo off
setlocal ENABLEEXTENSIONS

REM -----------------------------------------------------------------------------
REM SYSCO Oracle client deploy helper
REM Usage:
REM   deploy-client.bat SERVER_HOST_OR_IP DB_PASSWORD [APP_DIR]
REM Example:
REM   deploy-client.bat 192.168.1.50 MyStrongPwd123
REM   deploy-client.bat Israel-Masangu MyStrongPwd123 "C:\Program Files\SYSCO"
REM -----------------------------------------------------------------------------

if "%~1"=="" goto :usage
if "%~2"=="" goto :usage

set "SERVER=%~1"
set "DB_PASSWORD=%~2"
set "APP_DIR=%~3"

if "%APP_DIR%"=="" set "APP_DIR=%CD%"

if not exist "%APP_DIR%" (
  echo [ERROR] APP_DIR does not exist: "%APP_DIR%"
  exit /b 1
)

set "DB_FILE=%APP_DIR%\db.properties"

echo Writing db.properties to:
echo   %DB_FILE%
echo.

(
  echo db.vendor=oracle
  echo oracle.url=jdbc:oracle:thin:@//%SERVER%:1521/XEPDB1
  echo oracle.user=SYSCO_APP
  echo oracle.password=%DB_PASSWORD%
) > "%DB_FILE%"

if errorlevel 1 (
  echo [ERROR] Failed to write "%DB_FILE%"
  exit /b 1
)

echo [OK] Client configuration written successfully.
echo.
echo Next step:
echo   Launch SYSCO from this same APP_DIR (or ensure this is app working folder).
echo.
exit /b 0

:usage
echo.
echo Usage:
echo   %~nx0 SERVER_HOST_OR_IP DB_PASSWORD [APP_DIR]
echo.
echo Examples:
echo   %~nx0 192.168.1.50 MyStrongPwd123
echo   %~nx0 Israel-Masangu MyStrongPwd123 "C:\Program Files\SYSCO"
echo.
exit /b 1
