param(
    [Parameter(Mandatory = $true)]
    [string]$ServerHost,
    [int]$Port = 1521,
    [string]$ServiceName = "XEPDB1",
    [string]$AppUser = "SYSCO_APP",
    [string]$AppPassword = ""
)

$ErrorActionPreference = "Stop"

Write-Host "=== Oracle Client Connectivity Test ===" -ForegroundColor Cyan
Write-Host "Server    : $ServerHost"
Write-Host "Port      : $Port"
Write-Host "Service   : $ServiceName"
Write-Host "User      : $AppUser"
Write-Host ""

Write-Host "[1/4] DNS / host resolution..." -ForegroundColor Yellow
try {
    $resolved = Resolve-DnsName -Name $ServerHost -ErrorAction Stop
    ($resolved | Select-Object -First 3 | ForEach-Object { $_.IPAddress }) | ForEach-Object { Write-Host "  -> $_" }
} catch {
    Write-Host "WARNING: DNS resolution failed; trying direct TCP anyway." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[2/4] TCP connectivity..." -ForegroundColor Yellow
$tcp = Test-NetConnection -ComputerName $ServerHost -Port $Port -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-Host "FAILED: cannot reach $ServerHost`:$Port." -ForegroundColor Red
    Write-Host "Check server firewall, listener, and network routing."
    exit 1
}
Write-Host "OK: TCP reachable." -ForegroundColor Green

Write-Host ""
Write-Host "[3/4] Build JDBC URL..." -ForegroundColor Yellow
$jdbcUrl = "jdbc:oracle:thin:@//$ServerHost`:$Port/$ServiceName"
Write-Host "JDBC URL: $jdbcUrl" -ForegroundColor Green

Write-Host ""
Write-Host "[4/4] db.properties template for this client:" -ForegroundColor Yellow
Write-Host "db.vendor=oracle"
Write-Host "oracle.url=$jdbcUrl"
Write-Host "oracle.user=$AppUser"
if ([string]::IsNullOrWhiteSpace($AppPassword)) {
    Write-Host "oracle.password=<set-password>"
} else {
    Write-Host "oracle.password=$AppPassword"
}

Write-Host ""
Write-Host "Optional SQL*Plus test command:"
Write-Host "  sqlplus $AppUser/""<password>""@$ServerHost`:$Port/$ServiceName"
Write-Host ""
Write-Host "Client connectivity check complete." -ForegroundColor Green
