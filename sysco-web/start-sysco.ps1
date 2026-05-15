param(
    [string]$JdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1",
    [string]$DbUser = "sysco",
    [string]$DbPassword = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

Set-Location -Path $PSScriptRoot

$jarPath = Join-Path $PSScriptRoot "target\sysco-web-0.1.0-SNAPSHOT.jar"

if (-not $SkipBuild -or -not (Test-Path $jarPath)) {
    Write-Host "Building project (mvn clean package -DskipTests)..." -ForegroundColor Cyan
    mvn clean package -DskipTests
}

if (-not (Test-Path $jarPath)) {
    Write-Host "Jar not found at $jarPath" -ForegroundColor Red
    Write-Host "Check build output in target\ and update script jar name if needed." -ForegroundColor Yellow
    exit 1
}

$env:SYSCO_JDBC_URL = $JdbcUrl
$env:SYSCO_DB_USER = $DbUser
$env:SYSCO_DB_PASSWORD = $DbPassword

Write-Host "Starting SYSCO Web..." -ForegroundColor Green
Write-Host "JDBC URL: $JdbcUrl"
Write-Host "DB User : $DbUser"
Write-Host "URL     : http://localhost:8080"

java -jar $jarPath --spring.profiles.active=oracle
