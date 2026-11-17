# Starts the Prism backend (:8080) and frontend (:5173) in separate windows.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "Starting backend (:8080)…"
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d `"$root\backend`" && gradlew.bat bootRun"

Write-Host "Starting frontend (:5173)…"
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "cd /d `"$root\frontend`" && npm install && npm run dev"

Write-Host "Open http://localhost:5173"
