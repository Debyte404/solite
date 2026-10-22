@echo off
REM solite launcher for cmd.exe / PowerShell - same one-command UX as opencode.
setlocal

set "HERE=%~dp0"
set "JAR=%SOLITE_JAR%"
if "%JAR%"=="" if exist "%HERE%solite.jar" set "JAR=%HERE%solite.jar"
if "%JAR%"=="" if exist "%USERPROFILE%\solite\cli\solite.jar" set "JAR=%USERPROFILE%\solite\cli\solite.jar"
if "%JAR%"=="" if exist "%USERPROFILE%\solite\build\libs\solite-0.1.0-all.jar" set "JAR=%USERPROFILE%\solite\build\libs\solite-0.1.0-all.jar"

if "%JAR%"=="" (
  echo solite: jar not found. Build it with: cd /d "%USERPROFILE%\solite" ^&^& gradlew.bat installCli
  exit /b 1
)

REM JAVA_HOME is often set with forward slashes (C:/foo/bar), which cmd.exe
REM refuses to expand inside "if exist". Normalise to backslashes first.
set "JAVA="
if defined JAVA_HOME set "JH=%JAVA_HOME:/=\%"
if defined JH if exist "%JH%\bin\java.exe" set "JAVA=%JH%\bin\java.exe"

REM Fall back to the JDK this machine ships with.
if not defined JAVA if exist "%USERPROFILE%\jdk17\jdk-17.0.20.1+1\bin\java.exe" set "JAVA=%USERPROFILE%\jdk17\jdk-17.0.20.1+1\bin\java.exe"

REM Last resort: whatever java is on PATH.
if not defined JAVA for %%J in (java.exe) do set "JAVA=%%~$PATH:J"

if not defined JAVA (
  echo solite: no Java runtime found.
  echo solite: install JDK 17, or set JAVA_HOME to a JDK folder.
  exit /b 1
)

"%JAVA%" -Djline.terminal=jline -jar "%JAR%" %*
exit /b %ERRORLEVEL%
