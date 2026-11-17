@echo off
setlocal
set ROOT=%~dp0..
pushd "%ROOT%"
call gradlew.bat -q -PcoreOnly :ui-preview:installDist
if errorlevel 1 (popd & exit /b 1)
popd
call "%ROOT%\ui-preview\build\install\ui-preview\bin\ui-preview.bat" %*
