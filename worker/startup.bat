@echo off
setlocal
if not exist c:\Python goto :skipSetPath
set PATH=%PATH%;c:\Python;c:\Python\scripts
:skipSetPath

for /d %%i in (C:\app\virtualenv\*) do (
set PYTHONPATH=%%i\Lib\site-packages\
call %%i\Scripts\activate
)

if "%AWS_CONTAINER_CREDENTIALS_RELATIVE_URI%" equ "" (
    python %1
) else (
    for /f "tokens=*" %%i in ('python cosv2ParameterStoreMappings.py') do %%i
)
python %1
endlocal