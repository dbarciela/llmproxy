$port = 8081
$connection = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1

if ($connection) {
    $pidToKill = $connection.OwningProcess
    $procName = (Get-Process -Id $pidToKill -ErrorAction SilentlyContinue).ProcessName
    Write-Host "Port $port is currently in use by process ID $pidToKill ($procName)." -ForegroundColor Yellow
    $response = Read-Host "Do you want to terminate process $pidToKill? (Y/N)"
    if ($response -match '^[Yy]$') {
        Stop-Process -Id $pidToKill -Force
        Write-Host "Process $pidToKill has been terminated." -ForegroundColor Green
        Start-Sleep -Seconds 1
    } else {
        Write-Host "Process was not terminated. LlamaProxy might fail to start due to port conflict." -ForegroundColor Red
    }
}

Write-Host "Starting LlamaProxy..." -ForegroundColor Cyan
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Definition
Set-Location -Path "$scriptPath\backend"
.\mvnw clean spring-boot:run "-Dspring-boot.devtools.restart.enabled=false"
