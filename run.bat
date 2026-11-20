@echo off
setlocal
cd /d "%~dp0"
where mvn >nul 2>&1
if errorlevel 1 (
  echo Maven is not installed or mvn is not on PATH.
  echo Install Apache Maven, reopen this window, and try again.
  pause
  exit /b 1
)
call mvn javafx:run
if errorlevel 1 pause
endlocal
