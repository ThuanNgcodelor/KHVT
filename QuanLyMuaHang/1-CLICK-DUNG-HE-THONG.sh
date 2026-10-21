#!/usr/bin/env bash
# ==============================================================================
# KHVT: DỪNG TIẾN TRÌNH ỨNG DỤNG KHVT (LINUX)
# ==============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==================================================================="
echo "  KHVT: DUNG TIEN TRINH UNG DUNG KHVT (LINUX)"
echo "==================================================================="
echo ""

bash "$SCRIPT_DIR/CHAY_KHVT.sh" stop "$@"

echo ""
if [[ -t 0 ]]; then
  read -r -p "Bấm phím Enter để tiếp tục..." _
fi
