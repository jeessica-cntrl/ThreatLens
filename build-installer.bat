@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Checking Java 21...
java -version >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java is not installed or is not on PATH. Install JDK 21.
  exit /b 1
)
where jpackage >nul 2>&1
if errorlevel 1 (
  echo ERROR: jpackage was not found. Install JDK 21 (not only a JRE) and add its bin folder to PATH.
  exit /b 1
)

echo [2/4] Checking Maven...
where mvn >nul 2>&1
if errorlevel 1 (
  echo ERROR: Maven was not found. Install Apache Maven and add its bin folder to PATH.
  exit /b 1
)

echo [3/4] Checking WiX Toolset 3.x...
where candle >nul 2>&1
if errorlevel 1 (
  echo ERROR: WiX Toolset 3 was not found. Install WiX 3.11 and add its bin folder to PATH.
  exit /b 1
)
where light >nul 2>&1
if errorlevel 1 (
  echo ERROR: WiX light.exe was not found. Check your WiX PATH.
  exit /b 1
)

echo [4/4] Building application and Windows installer...
call mvn -B clean package
if errorlevel 1 (
  echo ERROR: Maven build failed. Read the errors above.
  exit /b 1
)
if exist installer rmdir /s /q installer
mkdir installer
jpackage --type exe --name ThreatLens --app-version 1.0.0 --vendor ThreatLens --description "Multi-Threaded Threat Detection" --input target --main-jar threatlens.jar --main-class threatlens.ui.Launcher --dest installer --win-menu --win-shortcut --win-dir-chooser --win-per-user-install
if errorlevel 1 (
  echo ERROR: jpackage failed. Confirm WiX 3.11 and JDK 21 are installed.
  exit /b 1
)
for %%F in (installer\ThreatLens-*.exe) do if /I not "%%~nxF"=="ThreatLens-Setup.exe" move /y "%%F" "installer\ThreatLens-Setup.exe" >nul
if not exist installer\ThreatLens-Setup.exe (
  echo ERROR: Installer output was not found.
  exit /b 1
)
echo.
echo SUCCESS: installer\ThreatLens-Setup.exe
endlocal
