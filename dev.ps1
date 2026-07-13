# dev.ps1 - Fast Development Environment Launcher
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Definition

Write-Host "Starting Aura Development Environment..." -ForegroundColor Cyan
Write-Host "This will open 3 separate windows for Backend, Frontend, and File Watcher." -ForegroundColor Cyan

# 1. Start Backend with DevTools enabled
Write-Host "Starting Spring Boot..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$scriptPath\backend'; .\mvnw clean spring-boot:run" -WindowStyle Normal

# 2. Start File Watcher
Write-Host "Starting Java File Watcher..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$scriptPath'; .\dev-watch.ps1" -WindowStyle Normal

# 3. Start Frontend with Vite HMR
Write-Host "Starting Vite Dev Server..." -ForegroundColor Green
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$scriptPath\frontend'; npm run dev" -WindowStyle Normal

Write-Host "All processes started!" -ForegroundColor Magenta
Write-Host "Frontend is running at http://localhost:5173" -ForegroundColor Yellow
Write-Host "Backend is running at http://localhost:8081" -ForegroundColor Yellow
