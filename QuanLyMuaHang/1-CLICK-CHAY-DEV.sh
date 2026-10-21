#!/usr/bin/env bash
# ==============================================================================
# KHVT: KHỞI ĐỘNG HỆ THỐNG Ở CHẾ ĐỘ DEVELOPMENT (CỤC BỘ TRÊN LINUX)
# Localhost: http://localhost:8080
# ==============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==================================================================="
echo "  KHVT: KHOI DONG HE THONG O CHE DO DEVELOPMENT (LINUX)"
echo "  Localhost: http://localhost:8080"
echo "==================================================================="
echo ""

bash "$SCRIPT_DIR/CHAY_KHVT.sh" start -Profile dev -OpenBrowser "$@"

echo ""
if [[ -t 0 ]]; then
  read -r -p "Bấm phím Enter để tiếp tục..." _
fi
