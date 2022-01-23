@echo off

docker build -t com.github.robtimus/windows-registry .
if %errorlevel% neq 0 exit /b %errorlevel%

docker run --rm -v "%cd%:C:/workspace" com.github.robtimus/windows-registry
if %errorlevel% neq 0 exit /b %errorlevel%
