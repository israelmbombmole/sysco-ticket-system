param(
    [string]$HostName = "localhost",
    [int]$Port = 1521,
    [string]$ServiceName = "XEPDB1",
    [string]$AppUser = "SYSCO_APP"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Oracle Server Check ===" -ForegroundColor Cyan
Write-Host "Host      : $HostName"
Write-Host "Port      : $Port"
Write-Host "Service   : $ServiceName"
Write-Host "App user  : $AppUser"
Write-Host ""

Write-Host "[1/5] Network port test..." -ForegroundColor Yellow
$tcp = Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-Host "FAILED: TCP $Port is not reachable on $HostName." -ForegroundColor Red
    Write-Host "Fix firewall/listener first."
    exit 1
}
Write-Host "OK: TCP $Port reachable." -ForegroundColor Green

Write-Host ""
Write-Host "[2/5] Oracle Windows services..." -ForegroundColor Yellow
$oracleServices = Get-Service | Where-Object { $_.Name -match "^Oracle.*" }
if (-not $oracleServices) {
    Write-Host "WARNING: No Oracle* services found on this machine." -ForegroundColor Yellow
} else {
    $oracleServices | Sort-Object Name | ForEach-Object {
        Write-Host ("{0,-45} {1}" -f $_.Name, $_.Status)
    }
}

Write-Host ""
Write-Host "[3/5] Listener status (lsnrctl status)..." -ForegroundColor Yellow
try {
    $listenerOutput = & lsnrctl status 2>&1
    $listenerOutput | ForEach-Object { Write-Host $_ }
} catch {
    Write-Host "WARNING: Could not run lsnrctl from PATH." -ForegroundColor Yellow
    Write-Host "If needed, run from Oracle bin folder."
}

Write-Host ""
Write-Host "[4/5] Check sqlplus availability..." -ForegroundColor Yellow
$sqlplusCmd = Get-Command sqlplus -ErrorAction SilentlyContinue
if (-not $sqlplusCmd) {
    Write-Host "WARNING: sqlplus not in PATH. Install Oracle client tools or use SQL Developer." -ForegroundColor Yellow
} else {
    Write-Host "OK: sqlplus found at $($sqlplusCmd.Source)" -ForegroundColor Green
}

Write-Host ""
Write-Host "[5/5] Next manual SQL checks (run in SQL*Plus):" -ForegroundColor Yellow
Write-Host "  CONNECT $AppUser/""<password>""@$HostName`:$Port/$ServiceName"
Write-Host "  SELECT COUNT(*) FROM user_tables;"
Write-Host "  SELECT table_name FROM user_tables WHERE table_name IN ('USERS','TICKETS','TICKET_TASKS');"
Write-Host ""
Write-Host "Server pre-check complete." -ForegroundColor Green
