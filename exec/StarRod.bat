@echo off
setlocal enabledelayedexpansion

echo Checking for Java...

@rem Check if Java is installed
java -version 2>nul >nul
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Java is not installed or not in PATH.
    echo        You must install Java ^(version ^>= 17^) to run Star Rod:
    echo        https://www.oracle.com/java/technologies/downloads/
    pause >nul
    exit /b 1
)

@rem Extract the Java version string
for /f "tokens=3 delims= " %%A in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "java_version=%%~A"
)

@rem Extract the major version number
for /f "tokens=1 delims=." %%A in ("%java_version%") do set "java_major=%%A"

echo Detected Java version: %java_version%

@rem Check if the major version is >= 17
if %java_major% lss 17 (
    echo.
    echo ERROR: Star Rod requires Java version 17 or greater.
    echo        Install a more recent version of Java to run Star Rod:
    echo        https://www.oracle.com/java/technologies/downloads/
    pause >nul
    exit /b 1
)

if not exist "StarRod.jar" (
    echo.
    echo ERROR: StarRod.jar not found in the current directory.
    echo        Please ensure that StarRod.jar is located in the same folder as this script.
    pause >nul
    exit /b 1
)

@rem Valid Java version detected, launch Star Rod
start "" javaw -jar -mx2G StarRod.jar
