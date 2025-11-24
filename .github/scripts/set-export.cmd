@echo off
setlocal

set "VAR_NAME=%1"
set "ARTIFACT_PATH=%2"

call set "REAL_PATH=%ARTIFACT_PATH%"

if exist "%REAL_PATH%\" (
    set "%VAR_NAME%=%REAL_PATH%"
    echo %VAR_NAME%=%REAL_PATH%>>"%GITHUB_ENV%"
)

endlocal