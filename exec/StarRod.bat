@echo off
setlocal

set "APP_DIR=%~dp0"
set "JAVA=%APP_DIR%runtime\bin\javaw.exe"

rem Use console Java when command-line arguments are supplied.
if not "%~1"=="" set "JAVA=%APP_DIR%runtime\bin\java.exe"

if not exist "%JAVA%" (
    echo ERROR: The bundled Java runtime is missing.
    echo        Re-extract the complete Star Rod release archive.
    pause >nul
    exit /b 1
)

cd /d "%APP_DIR%"

if "%~1"=="" (
    rem Launch the GUI without making this batch file wait.
    start "" "%JAVA%" -Xmx2G -jar "%APP_DIR%StarRod.jar"
    exit /b
)

rem Keep the console attached for command-line launches.
"%JAVA%" -Xmx2G -jar "%APP_DIR%StarRod.jar" %*
