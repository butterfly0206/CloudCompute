@echo off
set NUPKG_VERSION=1.0.0
set PACKAGE_NAME=Autodesk.Compute.WorkerManager

if "%BUILD_NUMBER%" neq "" set NUPKG_VERSION=1.0.%BUILD_NUMBER%
if "%BUILD_NUMBER%" neq "" echo Setting package version to %NUPKG_VERSION%

echo Environment:
set

if "%1" equ "/nugetonly" goto :nuget

echo Generating java client
docker run --rm -v %cd%:/local jimschubert/swagger-codegen-cli:2.3.1 generate -i /local/api.yaml -c /local/swaggergen-config.json -l java -o /local/client/
if not exist client goto :java_failed

echo Generating csharp client
docker run --rm -v %cd%:/local jimschubert/swagger-codegen-cli:2.3.1 generate -i /local/api.yaml -c /local/swaggergen-config-csharp.json -l csharp -o /local/client-csharp/ -DpackageName=%PACKAGE_NAME%

:nuget
if not exist client-csharp goto :csharp_failed
if not exist ".\nuget.exe" powershell -Command "(new-object System.Net.WebClient).DownloadFile('https://dist.nuget.org/win-x86-commandline/latest/nuget.exe', '.\nuget.exe')"
pushd client-csharp
..\nuget restore
..\nuget pack src\%PACKAGE_NAME%\%PACKAGE_NAME%.csproj -Build -Version %NUPKG_VERSION% -Properties Configuration=Release -Properties Platform=AnyCPU
popd
if not exist client-csharp\%PACKAGE_NAME%.%NUPKG_VERSION%.nupkg goto :nuget_pkg_failed

echo Nuget package client-csharp\%PACKAGE_NAME%.%NUPKG_VERSION%.nupkg is available for publishing
dir client-csharp\%PACKAGE_NAME%.%NUPKG_VERSION%.nupkg
goto :eof


:nuget_pkg_failed
echo ERROR: Could not create Nuget package client-csharp\%PACKAGE_NAME%.%NUPKG_VERSION%.nupkg for publishing
goto :eof
:csharp_failed
echo ERROR: Could not generate csharp client code
goto :eof
:java_failed
echo ERROR: Could not generate java client code
goto :eof
