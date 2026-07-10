# dev-watch.ps1 - Incremental Compilation Watcher for Spring Boot
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Definition
$folder = "$scriptPath\backend\src\main\java"
$filter = '*.java'

$watcher = New-Object IO.FileSystemWatcher $folder, $filter -Property @{
    IncludeSubdirectories = $true
    EnableRaisingEvents = $true
}

$action = {
    $path = $Event.SourceEventArgs.FullPath
    $changeType = $Event.SourceEventArgs.ChangeType
    Write-Host "`nFile $changeType : $path" -ForegroundColor Yellow
    Write-Host "Triggering incremental compilation..." -ForegroundColor Cyan
    
    # Save current location
    $currentLoc = Get-Location
    Set-Location -Path "$scriptPath\backend"
    
    # Run maven compile (only changed files are compiled)
    .\mvnw compile -q
    
    Set-Location -Path $currentLoc
    Write-Host "Compilation finished. Spring Boot DevTools should restart the context now." -ForegroundColor Green
}

Register-ObjectEvent $watcher "Changed" -Action $action
Register-ObjectEvent $watcher "Created" -Action $action
Register-ObjectEvent $watcher "Deleted" -Action $action
Register-ObjectEvent $watcher "Renamed" -Action $action

Write-Host "Watching $folder for changes. Press Ctrl+C to stop." -ForegroundColor Green

while ($true) {
    Start-Sleep -Seconds 1
}
