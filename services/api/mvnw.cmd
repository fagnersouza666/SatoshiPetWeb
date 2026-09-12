@echo off
setlocal

where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    call mvn %*
    exit /b %ERRORLEVEL%
)

echo Maven nao encontrado no PATH. Execute este projeto em um ambiente com Maven instalado. 1>&2
exit /b 1
