@echo off
REM Copyright (c) 2026 Olo Labs
REM SPDX-License-Identifier: Apache-2.0
setlocal enabledelayedexpansion

cd /d "%~dp0"

echo.
echo ========================================
echo   Olo Chat Backend
echo ========================================
echo.
echo   Backend:  http://localhost:7080
echo   Swagger:  http://localhost:7080/swagger-ui.html
echo.

REM Regional configuration (olo-configuration/<region>/)
if not defined OLO_CONFIGURATION_DIR (
  set "OLO_CONFIGURATION_DIR=%~dp0olo-configuration"
)
echo   Config:   %OLO_CONFIGURATION_DIR%
echo.

if not exist "%OLO_CONFIGURATION_DIR%" (
  echo ERROR: Configuration folder not found:
  echo   %OLO_CONFIGURATION_DIR%
  echo.
  echo Ensure olo-configuration exists in this repo, or set OLO_CONFIGURATION_DIR.
  pause
  exit /b 1
)

REM Load optional .env overrides (KEY=VALUE lines)
if exist ".env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
    if not "%%a"=="" set "%%a=%%b"
  )
)

call :EnsureJava
if errorlevel 1 (
  pause
  exit /b 1
)

echo ========================================
echo.
call gradlew.bat bootRun
pause
exit /b %ERRORLEVEL%

:EnsureJava
REM Prefer JDK 21 when installed; Gradle toolchain auto-downloads JDK 21 if needed.
for %%v in (21 22 23 24 25) do (
  if exist "%ProgramFiles%\Microsoft\jdk-%%v" (
    for /d %%d in ("%ProgramFiles%\Microsoft\jdk-%%v*") do (
      set "JAVA_HOME=%%~d"
      set "PATH=%%~d\bin;%PATH%"
      goto :JavaReady
    )
  )
  if exist "%ProgramFiles%\Eclipse Adoptium\jdk-%%v" (
    for /d %%d in ("%ProgramFiles%\Eclipse Adoptium\jdk-%%v*") do (
      set "JAVA_HOME=%%~d"
      set "PATH=%%~d\bin;%PATH%"
      goto :JavaReady
    )
  )
)

where java >nul 2>&1
if not errorlevel 1 (
  for /f "tokens=3" %%j in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER=%%j"
  )
  set "JAVA_VER=!JAVA_VER:"=!"
  echo !JAVA_VER! | findstr /r "^21\." >nul && goto :JavaReady
  echo !JAVA_VER! | findstr /r "^2[2-9]\." >nul && goto :JavaReady
)

echo JDK 21 not found locally. Installing Microsoft OpenJDK 21...
set "INSTALLED=0"
where winget >nul 2>&1
if not errorlevel 1 (
  winget install -e --id Microsoft.OpenJDK.21 --accept-package-agreements --accept-source-agreements
  if not errorlevel 1 set "INSTALLED=1"
)

if "%INSTALLED%"=="1" (
  for /d %%d in ("%ProgramFiles%\Microsoft\jdk-21*") do (
    set "JAVA_HOME=%%~d"
    set "PATH=%%~d\bin;%PATH%"
    goto :JavaReady
  )
)

echo   Java:     Gradle will use or auto-download JDK 21 on first run
goto :JavaReady

:JavaReady
if defined JAVA_HOME (
  echo   Java:     %JAVA_HOME%
)
echo.
exit /b 0
