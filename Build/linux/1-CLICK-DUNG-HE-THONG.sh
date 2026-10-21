#!/usr/bin/env bash
# ==============================================================================
# KHVT: DỪNG TIẾN TRÌNH ỨNG DỤNG KHVT (LINUX)
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
echo "  KHVT: DUNG TIEN TRINH UNG DUNG KHVT (LINUX)"
echo "==================================================================="
echo ""

bash "$KHVT_DIR/CHAY_KHVT.sh" stop "$@"

echo ""
if [[ -t 0 ]]; then
  read -r -p "Bấm phím Enter để tiếp tục..." _
fi
