#!/usr/bin/env bash
# ==============================================================================
# KHVT: Launcher khởi động ứng dụng trên Linux
# Hỗ trợ MySQL / Redis nội bộ hoặc qua Docker
# ==============================================================================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bash "$SCRIPT_DIR/tools/run-linux.sh" "$@"
KHVT_EXIT_CODE=$?

if [[ $KHVT_EXIT_CODE -ne 0 ]]; then
  echo ""
  echo "KHVT chưa chạy được. Đọc thông báo ở trên và xem log trong source/target/runtime."
fi

exit $KHVT_EXIT_CODE
