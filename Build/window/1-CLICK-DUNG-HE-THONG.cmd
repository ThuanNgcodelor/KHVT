@echo off
chcp 65001 >nul
echo ===================================================================
echo   KHVT: DUNG TIEN TRINH UNG DUNG KHVT
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

call "%KHVT_CMD%" stop
echo.
pause
