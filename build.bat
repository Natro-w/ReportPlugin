@echo off
REM ==========================================
REM  ReportPlugin Build Script for Windows
REM ==========================================
setlocal enabledelayedexpansion

set LUMI_JAR=..\..\Servers\lumi-server\Lumi-1.6.0.jar
set LP_JAR=..\..\Servers\lumi-server\plugins\LuckPerms-Nukkit-5.5.55.jar
set SRC=src\main\java
set OUT=target\classes
set RES=src\main\resources
set JAR_OUT=target\ReportPlugin-1.1.0.jar

REM Clean
if exist target rmdir /s /q target
mkdir %OUT% 2>nul

REM Compile
javac -cp "%LUMI_JAR%;%LP_JAR%" -d %OUT% %SRC%\ru\Natro\reportplugin\*.java
if %errorlevel% neq 0 (
    echo Build failed!
    exit /b %errorlevel%
)

REM Package
cd %OUT%
jar cf ..\..\%JAR_OUT% -C . .
jar uf ..\..\%JAR_OUT% -C ..\..\%RES% plugin.yml
jar uf ..\..\%JAR_OUT% -C ..\..\%RES% config.yml
cd ..\..

echo.
echo Build successful: %JAR_OUT%
