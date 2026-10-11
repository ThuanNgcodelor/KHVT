@echo off
chcp 65001 >nul
echo ===================================================================
echo   KHVT: KHOI DONG HE THONG O CHE DO DEVELOPMENT (CUC BO)
echo   Localhost: http://localhost:8080
echo ===================================================================
echo.
call "%~dp0CHAY_KHVT.cmd" start -Profile dev -OpenBrowser
echo.
pause
