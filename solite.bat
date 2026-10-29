@echo off
setlocal
cd /d "%~dp0"

if exist "%~dp0solite.exe" (
    "%~dp0solite.exe" %*
) else if exist "%~dp0build\dist\solite\solite.exe" (
    "%~dp0build\dist\solite\solite.exe" %*
) else if exist "%~dp0build\install\solite\bin\solite.bat" (
    call "%~dp0build\install\solite\bin\solite.bat" %*
) else (
    call "%~dp0gradlew.bat" run --quiet --console=plain
)
