@echo off
setlocal

if not "%1"=="" (
    set "to_extract=%ARTIFACT_PATH_PREFIX%\%1.tar"
)

for %%i in (%to_extract%) do (
    if exist "%%i" (
        tar -xf "%%i" -C "%ARTIFACT_PATH_PREFIX%"
        echo Unpacked %%i in %CD%\%ARTIFACT_PATH_PREFIX%
    ) else (
        echo file not found in %ARTIFACT_PATH_PREFIX%
        dir "%ARTIFACT_PATH_PREFIX%"
    )
)

endlocal