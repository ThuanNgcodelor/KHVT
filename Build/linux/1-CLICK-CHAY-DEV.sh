#!/usr/bin/env bash
# ==============================================================================
# KHVT: KHỞI ĐỘNG HỆ THỐNG Ở CHẾ ĐỘ DEVELOPMENT (CỤC BỘ TRÊN LINUX)
# Localhost: http://localhost:8080
# ==============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Tìm thư mục chứa CHAY_KHVT.sh
if [[ -f "$SCRIPT_DIR/../../QuanLyMuaHang/CHAY_KHVT.sh" ]]; then
  KHVT_DIR="$(cd "$SCRIPT_DIR/../../QuanLyMuaHang" && pwd)"
elif [[ -f "$SCRIPT_DIR/../QuanLyMuaHang/CHAY_KHVT.sh" ]]; then
  KHVT_DIR="$(cd "$SCRIPT_DIR/../QuanLyMuaHang" && pwd)"
elif [[ -f "$SCRIPT_DIR/CHAY_KHVT.sh" ]]; then
  KHVT_DIR="$SCRIPT_DIR"
else
  echo "Lỗi: Không tìm thấy thư mục dự án QuanLyMuaHang hoặc CHAY_KHVT.sh!" >&2
  exit 1
fi

echo "==================================================================="
echo "  KHVT: KHOI DONG HE THONG O CHE DO DEVELOPMENT (LINUX)"
echo "  Localhost: http://localhost:8080"
echo "==================================================================="
echo ""

bash "$KHVT_DIR/CHAY_KHVT.sh" start -Profile dev -OpenBrowser "$@"

echo ""
if [[ -t 0 ]]; then
  read -r -p "Bấm phím Enter để tiếp tục..." _
fi
