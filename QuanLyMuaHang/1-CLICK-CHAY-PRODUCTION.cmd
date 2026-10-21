@echo off
chcp 65001 >nul
echo ===================================================================
echo   KHVT: KHOI DONG HE THONG O CHE DO PRODUCTION (PUBLIC DOMAIN)
echo   Domain: https://khvthub.com
echo ===================================================================
echo.
call "%~dp0CHAY_KHVT.cmd" start -Profile prod

echo.
pause
