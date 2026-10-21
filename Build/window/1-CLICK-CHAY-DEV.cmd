@echo off
chcp 65001 >nul
echo ===================================================================
echo   KHVT: KHOI DONG HE THONG O CHE DO DEVELOPMENT (CUC BO)
echo   Localhost: http://localhost:8080
echo ===================================================================
echo.

set "KHVT_CMD="
if exist "%~dp0..\..\QuanLyMuaHang\CHAY_KHVT.cmd" set "KHVT_CMD=%~dp0..\..\QuanLyMuaHang\CHAY_KHVT.cmd"
if not defined KHVT_CMD if exist "%~dp0..\QuanLyMuaHang\CHAY_KHVT.cmd" set "KHVT_CMD=%~dp0..\QuanLyMuaHang\CHAY_KHVT.cmd"
if not defined KHVT_CMD if exist "%~dp0CHAY_KHVT.cmd" set "KHVT_CMD=%~dp0CHAY_KHVT.cmd"

if not defined KHVT_CMD (
    echo [LOI] Khong tim thay CHAY_KHVT.cmd trong thu muc QuanLyMuaHang!
    pause
    exit /b 1
)

call "%KHVT_CMD%" start -Profile dev -OpenBrowser
echo.
pause
