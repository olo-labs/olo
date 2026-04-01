@echo off
setlocal enabledelayedexpansion
echo.
echo Stopping Olo backend (port 7080)...
set FOUND=0
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr :7080 ^| findstr LISTENING') do (
  taskkill /PID %%a /F >nul 2>&1
  set FOUND=1
)
if !FOUND!==0 (
  echo No process found listening on port 7080.
) else (
  echo Backend stopped.
)
echo.
pause
