@echo off
setlocal
chcp 65001 >nul
rem Khoi dong ung dung KHVT (Ho tro chay truc tiep voi Laragon hoac Docker)
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\run-windows.ps1" %*
set "KHVT_EXIT_CODE=%ERRORLEVEL%"
if not "%KHVT_EXIT_CODE%"=="0" (
  echo.
  echo KHVT chua chay duoc. Doc thong bao o tren va log rieng trong source\target\runtime.
)
if "%~1"=="" pause
exit /b %KHVT_EXIT_CODE%
