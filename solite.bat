@echo off
if exist "%~dp0build\dist\solite\solite.exe" (
    "%~dp0build\dist\solite\solite.exe" %*
) else (
    "%~dp0build\install\solite\bin\solite.bat" %*
)
