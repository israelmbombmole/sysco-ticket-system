param(
    [string]$DbPassword = "",
    [string]$DbUser = "sysco",
    [string]$JdbcUrl = "jdbc:oracle:thin:@localhost:1521/FREEPDB1",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

Write-Host "Preparing SYSCO for LAN access..." -ForegroundColor Cyan

$env:SERVER_ADDRESS = "0.0.0.0"
$env:SYSCO_DB_USER = $DbUser
$env:SYSCO_DB_PASSWORD = $DbPassword
$env:SYSCO_JDBC_URL = $JdbcUrl

if (-not $SkipBuild) {
    Write-Host "Building jar (mvn clean package -DskipTests)..." -ForegroundColor Cyan
    mvn clean package -DskipTests
}

$jar = Join-Path $PSScriptRoot "target\sysco-web-0.1.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
    $jar = (Get-ChildItem -Path (Join-Path $PSScriptRoot "target") -Filter "*.jar" |
        Where-Object { $_.Name -notlike "*sources*" -and $_.Name -notlike "*javadoc*" } |
        Select-Object -First 1).FullName
}
if (-not $jar -or -not (Test-Path $jar)) {
    throw "Could not find built jar in .\target\"
}

$ruleName = "SYSCO Web 8080"
if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
    Write-Host "Opening Windows Firewall port 8080..." -ForegroundColor Yellow
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow | Out-Null
}

$ip = (Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -notlike "127.*" -and $_.PrefixOrigin -ne "WellKnown" } |
    Select-Object -First 1 -ExpandProperty IPAddress)

Write-Host ""
Write-Host "Server starting..." -ForegroundColor Green
Write-Host "Local URL : http://localhost:8080"
if ($ip) {
    Write-Host "LAN URL   : http://$ip`:8080" -ForegroundColor Green
    Write-Host "Use this LAN URL on other PCs."
}
Write-Host ""

java -jar $jar --spring.profiles.active=oracle
