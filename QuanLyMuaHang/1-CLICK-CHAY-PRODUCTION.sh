#!/usr/bin/env bash
# ==============================================================================
# KHVT: KHỞI ĐỘNG HỆ THỐNG Ở CHẾ ĐỘ PRODUCTION (LINUX)
# Domain: https://khvthub.com
# ==============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==================================================================="
echo "  KHVT: KHOI DONG HE THONG O CHE DO PRODUCTION (PUBLIC DOMAIN)"
echo "  Domain: https://khvthub.com"
echo "==================================================================="
echo ""

bash "$SCRIPT_DIR/CHAY_KHVT.sh" start -Profile prod -NoBuild "$@"
if [[ $? -ne 0 ]]; then
  echo ""
  echo "Đang build lại và khởi động hệ thống..."
  bash "$SCRIPT_DIR/CHAY_KHVT.sh" start -Profile prod "$@"
fi

echo ""
if [[ -t 0 ]]; then
  read -r -p "Bấm phím Enter để tiếp tục..." _
fi
