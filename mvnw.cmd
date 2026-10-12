@REM Maven Wrapper Batch Script for Windows
@echo off
setlocal

where mvn >nul 2>nul
if %ERRORLEVEL% equ 0 (
    mvn %*
    goto end
)

echo Maven is not found on PATH. Please install Maven or use Docker: docker compose up --build
exit /b 1

:end
endlocal
