#!/usr/bin/env bash
# ==============================================================================
# KHVT Linux Launcher
# Tương đương tools/run-windows.ps1 trên Linux
# ==============================================================================
set -euo pipefail

ACTION="start"
PROFILE="dev"
NO_BUILD=false
EXTERNAL_DOCKER=false
APP_PORT=0
HEAP_MB=256
OPEN_BROWSER=false

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_DIR="$REPO_ROOT/source"
FRONTEND_DIR="$SOURCE_DIR/frontend"
RUNTIME_DIR="$SOURCE_DIR/target/runtime"
STATE_PATH="$RUNTIME_DIR/linux-app.json"
PRIVATE_ENV_PATH="$SOURCE_DIR/.env"
JAR_NAME="quan-ly-mua-hang-0.0.1-SNAPSHOT"
JAR_PATH="$SOURCE_DIR/target/$JAR_NAME.jar"

# Đọc tham số dòng lệnh
while [[ $# -gt 0 ]]; do
  case "$1" in
    start|Start|START)
      ACTION="start"
      shift
      ;;
    stop|Stop|STOP)
      ACTION="stop"
      shift
      ;;
    status|Status|STATUS)
      ACTION="status"
      shift
      ;;
    -Profile|--profile|-profile)
      PROFILE="$2"
      shift 2
      ;;
    -NoBuild|--no-build|--nobuild|-nobuild)
      NO_BUILD=true
      shift
      ;;
    -ExternalDocker|--external-docker|-externaldocker)
      EXTERNAL_DOCKER=true
      shift
      ;;
    -Port|--port|-port)
      APP_PORT="$2"
      shift 2
      ;;
    -HeapMB|--heap|--heap-mb|-heap)
      HEAP_MB="$2"
      shift 2
      ;;
    -OpenBrowser|--open-browser|-openbrowser)
      OPEN_BROWSER=true
      shift
      ;;
    -h|--help)
      echo "Sử dụng: $0 [start|stop|status] [tùy chọn]"
      echo "  start               Khởi động ứng dụng (mặc định)"
      echo "  stop                Dừng ứng dụng đang chạy"
      echo "  status              Xem trạng thái hoạt động"
      echo "Tùy chọn:"
      echo "  -Profile [dev|prod] Chọn profile Spring (mặc định: dev)"
      echo "  -NoBuild            Bỏ qua build frontend & backend nếu đã có JAR"
      echo "  -ExternalDocker     Không tự chạy docker compose up -d"
      echo "  -Port <port>        Cổng ứng dụng (mặc định: 8080)"
      echo "  -HeapMB <mb>        Dung lượng RAM tối đa cho JVM (mặc định: 256MB)"
      echo "  -OpenBrowser        Tự động mở trình duyệt sau khi khởi động xong"
      exit 0
      ;;
    *)
      echo "Tùy chọn không hợp lệ: $1. Dùng $0 --help để xem hướng dẫn." >&2
      exit 1
      ;;
  esac
done

mkdir -p "$RUNTIME_DIR"

# ------------------------------------------------------------------------------
# Tiện ích: Đọc cấu hình môi trường từ .env
# ------------------------------------------------------------------------------
read_private_env() {
  if [[ ! -f "$PRIVATE_ENV_PATH" ]]; then
    if [[ -f "$SOURCE_DIR/env" ]]; then
      echo "Tự động sao chép cấu hình từ source/env sang source/.env..."
      cp "$SOURCE_DIR/env" "$PRIVATE_ENV_PATH"
    elif [[ -f "$SOURCE_DIR/.env.example" ]]; then
      echo "Tự động khởi tạo source/.env từ source/.env.example..."
      cp "$SOURCE_DIR/.env.example" "$PRIVATE_ENV_PATH"
    else
      echo "Lỗi: Không tìm thấy source/.env. Hãy tạo file cấu hình trước khi chạy." >&2
      exit 1
    fi
  fi

  while IFS= read -r line || [[ -n "$line" ]]; do
    # Bỏ qua dòng trống và comment
    line="$(echo "$line" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    [[ -z "$line" || "$line" =~ ^# ]] && continue

    if [[ "$line" =~ ^(export[[:space:]]+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
      var_name="${BASH_REMATCH[2]}"
      var_val="${BASH_REMATCH[3]}"
      # Xóa dấu nháy bao quanh nếu có
      if [[ "$var_val" =~ ^\"(.*)\"$ || "$var_val" =~ ^\'(.*)\'$ ]]; then
        var_val="${BASH_REMATCH[1]}"
      fi
      export "$var_name=$var_val"
    fi
  done < "$PRIVATE_ENV_PATH"

  # Điều chỉnh đường dẫn Font Unicode trên Linux nếu cấu hình đang trỏ đường dẫn Windows
  if [[ -z "${APP_PDF_FONT_PATH:-}" || "$APP_PDF_FONT_PATH" =~ ^[A-Za-z]: || ! -f "$APP_PDF_FONT_PATH" ]]; then
    local candidate_fonts=(
      "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
      "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"
      "/usr/share/fonts/truetype/freefont/FreeSans.ttf"
    )
    for font in "${candidate_fonts[@]}"; do
      if [[ -f "$font" ]]; then
        export APP_PDF_FONT_PATH="$font"
        break
      fi
    done
  fi

  # Điều chỉnh thư mục lưu trữ file nếu đang trỏ đường dẫn Windows
  if [[ -z "${FILE_STORAGE_ROOT:-}" || "$FILE_STORAGE_ROOT" =~ ^[A-Za-z]: ]]; then
    export FILE_STORAGE_ROOT="$SOURCE_DIR/data/files"
  fi
  mkdir -p "$FILE_STORAGE_ROOT"
}

# ------------------------------------------------------------------------------
# Tiện ích: Tìm kiếm JDK 21
# ------------------------------------------------------------------------------
find_jdk21() {
  local candidates=()
  [[ -n "${JAVA_HOME:-}" ]] && candidates+=("$JAVA_HOME/bin/java")
  candidates+=(
    "$HOME/.local/share/jdk-21/bin/java"
    "/opt/java/openjdk/bin/java"
    "$REPO_ROOT/tools/jdk-21/bin/java"
  )
  if command -v java >/dev/null 2>&1; then
    candidates+=("$(command -v java)")
  fi

  # Tìm trong /usr/lib/jvm
  if [[ -d /usr/lib/jvm ]]; then
    while IFS= read -r f; do
      [[ -n "$f" ]] && candidates+=("$f")
    done < <(find /usr/lib/jvm -maxdepth 3 -name java -type f 2>/dev/null || true)
  fi

  for c in "${candidates[@]}"; do
    if [[ -x "$c" ]]; then
      local v_out
      v_out="$("$c" -version 2>&1 || true)"
      if echo "$v_out" | grep -qE '(openjdk|java) version "(21\.|21 )|(openjdk|java) 21'; then
        echo "$c"
        return 0
      fi
    fi
  done

  # Nếu chưa có trên máy, kiểm tra xem docker có image chứa JDK 21 không để tự trích xuất
  if command -v docker >/dev/null 2>&1; then
    local d_img="maven:3.9.11-eclipse-temurin-21"
    if docker image inspect "$d_img" >/dev/null 2>&1; then
      echo "Đang tự động chuẩn bị OpenJDK 21 portable từ docker image..." >&2
      mkdir -p "$HOME/.local/share/jdk-21"
      docker run --rm "$d_img" tar -cf - -C /opt/java/openjdk . | tar -xf - -C "$HOME/.local/share/jdk-21"
      if [[ -x "$HOME/.local/share/jdk-21/bin/java" ]]; then
        echo "$HOME/.local/share/jdk-21/bin/java"
        return 0
      fi
    fi
  fi

  echo "Lỗi: Cần JDK 21. Cài đặt openjdk-21-jdk hoặc đặt JAVA_HOME tới JDK 21." >&2
  exit 1
}

# ------------------------------------------------------------------------------
# Tiện ích: Tìm kiếm Maven 3.9+
# ------------------------------------------------------------------------------
find_maven() {
  local candidates=()
  [[ -n "${MAVEN_HOME:-}" ]] && candidates+=("$MAVEN_HOME/bin/mvn")
  candidates+=(
    "$HOME/.local/share/maven/bin/mvn"
    "$REPO_ROOT/tools/maven/bin/mvn"
  )
  if command -v mvn >/dev/null 2>&1; then
    candidates+=("$(command -v mvn)")
  fi

  for c in "${candidates[@]}"; do
    if [[ -x "$c" ]]; then
      local m_out
      m_out="$("$c" -version 2>&1 || true)"
      if echo "$m_out" | grep -qE 'Apache Maven 3\.(9|[1-9][0-9])|Apache Maven [4-9]\.'; then
        echo "$c"
        return 0
      fi
    fi
  done

  # Tự động trích xuất từ docker image nếu có
  if command -v docker >/dev/null 2>&1; then
    local d_img="maven:3.9.11-eclipse-temurin-21"
    if docker image inspect "$d_img" >/dev/null 2>&1; then
      echo "Đang tự động chuẩn bị Maven 3.9 portable từ docker image..." >&2
      mkdir -p "$HOME/.local/share/maven"
      docker run --rm "$d_img" tar -cf - -C /usr/share/maven . | tar -xf - -C "$HOME/.local/share/maven"
      if [[ -x "$HOME/.local/share/maven/bin/mvn" ]]; then
        echo "$HOME/.local/share/maven/bin/mvn"
        return 0
      fi
    fi
  fi

  echo "Lỗi: Cần Maven 3.9+ trong PATH hoặc MAVEN_HOME." >&2
  exit 1
}

# ------------------------------------------------------------------------------
# Tiện ích: Kiểm tra port TCP có đang mở không
# ------------------------------------------------------------------------------
is_port_listening() {
  local host="$1"
  local port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z "$host" "$port" 2>/dev/null
    return $?
  fi
  (echo > "/dev/tcp/$host/$port") 2>/dev/null
  return $?
}

# ------------------------------------------------------------------------------
# Tiện ích: Kiểm tra port có đang rảnh không
# ------------------------------------------------------------------------------
assert_port_free() {
  local port="$1"
  if is_port_listening "127.0.0.1" "$port"; then
    echo "Lỗi: Cổng $port đang được sử dụng." >&2
    echo "Hãy kiểm tra tiến trình đang chiếm cổng hoặc dùng lệnh stop trước:" >&2
    echo "  $0 stop" >&2
    echo "Hoặc chọn cổng khác bằng tham số: -Port <số_cổng>" >&2
    exit 1
  fi
}

# ------------------------------------------------------------------------------
# Tiện ích: Kiểm tra tiến trình KHVT đang chạy
# ------------------------------------------------------------------------------
get_tracked_process() {
  if [[ ! -f "$STATE_PATH" ]]; then
    echo ""
    return 0
  fi

  local pid
  pid="$(python3 -c "import json; print(json.load(open('$STATE_PATH')).get('pid', ''))" 2>/dev/null || true)"
  if [[ -z "$pid" || ! "$pid" =~ ^[0-9]+$ ]]; then
    rm -f "$STATE_PATH"
    echo ""
    return 0
  fi

  # Kiểm tra PID còn sống không
  if kill -0 "$pid" 2>/dev/null; then
    if [[ -f "/proc/$pid/cmdline" ]]; then
      local cmdline
      cmdline="$(tr '\0' ' ' < "/proc/$pid/cmdline" 2>/dev/null || true)"
      if [[ "$cmdline" == *"$JAR_NAME.jar"* || "$cmdline" == *"quan-ly-mua-hang"* ]]; then
        echo "$pid"
        return 0
      fi
    fi
  fi

  # Tiến trình đã chết, dọn dẹp state cũ
  rm -f "$STATE_PATH"
  echo ""
  return 0
}

test_app_health() {
  local port="$1"
  if command -v curl >/dev/null 2>&1; then
    local res
    res="$(curl -s -m 2 "http://127.0.0.1:$port/actuator/health" 2>/dev/null || true)"
    if echo "$res" | grep -q '"status":"UP"'; then
      return 0
    fi
  fi
  return 1
}

# ------------------------------------------------------------------------------
# Đảm bảo dịch vụ Docker (MySQL / Redis)
# ------------------------------------------------------------------------------
ensure_docker_services() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "Lỗi: Cần cài đặt Docker để chạy MySQL/Redis của dự án." >&2
    exit 1
  fi

  if ! docker info >/dev/null 2>&1; then
    echo "Lỗi: Docker engine chưa chạy. Hãy khởi động Docker daemon trước." >&2
    exit 1
  fi

  local compose_cmd=(docker compose --project-name qmh-local --env-file "$PRIVATE_ENV_PATH" --file "$SOURCE_DIR/docker-compose.yml")

  if [[ "$EXTERNAL_DOCKER" == false ]]; then
    echo "Khởi động MySQL/Redis của dự án qua Docker Compose..."
    "${compose_cmd[@]}" up -d mysql redis >/dev/null 2>&1 || true
  fi

  echo "Kiểm tra trạng thái sẵn sàng của MySQL và Redis..."
  local timer=0
  while [[ $timer -lt 60 ]]; do
    local mysql_ok=false
    local redis_ok=false

    local mysql_port=3306
    [[ "${SPRING_DATASOURCE_URL:-}" =~ :([0-9]+)/ ]] && mysql_port="${BASH_REMATCH[1]}"
    local redis_port="${REDIS_PORT:-6379}"

    is_port_listening "127.0.0.1" "$mysql_port" && mysql_ok=true
    is_port_listening "127.0.0.1" "$redis_port" && redis_ok=true

    if [[ "$mysql_ok" == true && "$redis_ok" == true ]]; then
      return 0
    fi

    sleep 1
    timer=$((timer + 1))
  done

  echo "Cảnh báo: Hết 60s chờ MySQL/Redis. Ứng dụng sẽ thử kết nối trực tiếp..."
}

# ==============================================================================
# XỬ LÝ HÀNH ĐỘNG
# ==============================================================================

# ------------------------------------------------------------------------------
# Action: STOP
# ------------------------------------------------------------------------------
if [[ "$ACTION" == "stop" ]]; then
  tracked_pid="$(get_tracked_process)"
  if [[ -z "$tracked_pid" ]]; then
    echo "Không có tiến trình KHVT do launcher này quản lý đang chạy."
    exit 0
  fi

  echo "Đang dừng tiến trình KHVT (PID $tracked_pid)..."
  kill -TERM "$tracked_pid" 2>/dev/null || true

  wait_timer=0
  while kill -0 "$tracked_pid" 2>/dev/null && [[ $wait_timer -lt 10 ]]; do
    sleep 1
    wait_timer=$((wait_timer + 1))
  done

  if kill -0 "$tracked_pid" 2>/dev/null; then
    echo "Tiến trình chưa dừng, buộc dừng (SIGKILL)..."
    kill -KILL "$tracked_pid" 2>/dev/null || true
  fi

  rm -f "$STATE_PATH"
  echo "Đã dừng ứng dụng KHVT. MySQL/Redis và Docker tiếp tục chạy."
  exit 0
fi

# ------------------------------------------------------------------------------
# Action: STATUS
# ------------------------------------------------------------------------------
if [[ "$ACTION" == "status" ]]; then
  tracked_pid="$(get_tracked_process)"
  if [[ -z "$tracked_pid" ]]; then
    echo "Ứng dụng KHVT do launcher này quản lý chưa chạy."
    exit 0
  fi

  port="$(python3 -c "import json; print(json.load(open('$STATE_PATH')).get('port', 8080))" 2>/dev/null || echo "8080")"
  url="$(python3 -c "import json; print(json.load(open('$STATE_PATH')).get('url', 'http://localhost:$port'))" 2>/dev/null || echo "http://localhost:$port")"

  if test_app_health "$port"; then
    echo "KHVT PID $tracked_pid, health UP, địa chỉ $url."
  else
    echo "KHVT PID $tracked_pid, health chưa UP, địa chỉ $url."
  fi
  exit 0
fi

# ------------------------------------------------------------------------------
# Action: START
# ------------------------------------------------------------------------------
tracked_pid="$(get_tracked_process)"
if [[ -n "$tracked_pid" ]]; then
  port="$(python3 -c "import json; print(json.load(open('$STATE_PATH')).get('port', 8080))" 2>/dev/null || echo "8080")"
  url="$(python3 -c "import json; print(json.load(open('$STATE_PATH')).get('url', 'http://localhost:$port'))" 2>/dev/null || echo "http://localhost:$port")"

  if test_app_health "$port"; then
    echo "KHVT đang chạy: $url. Không build/chạy thêm tiến trình."
    if [[ "$OPEN_BROWSER" == true ]]; then
      command -v xdg-open >/dev/null 2>&1 && xdg-open "$url" >/dev/null 2>&1 || true
    fi
    exit 0
  else
    echo "Ứng dụng được theo dõi (PID $tracked_pid) vẫn đang chạy nhưng chưa healthy." >&2
    echo "Dùng $0 stop trước khi chạy lại." >&2
    exit 1
  fi
fi

# Nạp file môi trường
read_private_env

ACTIVE_PROFILE="${PROFILE:-${SPRING_PROFILES_ACTIVE:-dev}}"
if [[ "$ACTIVE_PROFILE" != "dev" && "$ACTIVE_PROFILE" != "prod" ]]; then
  echo "Lỗi: Profile phải là dev hoặc prod." >&2
  exit 1
fi

if [[ $APP_PORT -le 0 ]]; then
  if [[ -n "${SERVER_PORT:-}" && "${SERVER_PORT:-}" =~ ^[0-9]+$ ]]; then
    APP_PORT="$SERVER_PORT"
  else
    APP_PORT=8080
  fi
fi

# Kiểm tra port rảnh
assert_port_free "$APP_PORT"

export SERVER_PORT="$APP_PORT"
export SERVER_ADDRESS="127.0.0.1"
export SPRING_PROFILES_ACTIVE="$ACTIVE_PROFILE"

if [[ "$ACTIVE_PROFILE" == "prod" ]]; then
  export SESSION_COOKIE_SECURE="true"
  APP_URL="${APP_FRONTEND_URL:-http://localhost:$APP_PORT}"
else
  export APP_FRONTEND_URL="http://localhost:$APP_PORT"
  export SESSION_COOKIE_SECURE="false"
  APP_URL="$APP_FRONTEND_URL"
fi

export NODE_OPTIONS="--max-old-space-size=256 --max-semi-space-size=4"
export MAVEN_OPTS="-XX:ActiveProcessorCount=2 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Xms16m -Xmx128m -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m"

# Tìm JDK 21
JAVA_EXE="$(find_jdk21)"
JAVA_BIN_DIR="$(dirname "$JAVA_EXE")"
JAVA_HOME_DIR="$(dirname "$JAVA_BIN_DIR")"
export JAVA_HOME="$JAVA_HOME_DIR"
export PATH="$JAVA_BIN_DIR:$PATH"

# Kiểm tra MySQL và Redis
TARGET_DB_PORT=3306
[[ "${SPRING_DATASOURCE_URL:-}" =~ :([0-9]+)/ ]] && TARGET_DB_PORT="${BASH_REMATCH[1]}"
TARGET_REDIS_PORT="${REDIS_PORT:-6379}"

if is_port_listening "127.0.0.1" "$TARGET_DB_PORT" && is_port_listening "127.0.0.1" "$TARGET_REDIS_PORT"; then
  echo "Phát hiện MySQL (cổng $TARGET_DB_PORT) và Redis (cổng $TARGET_REDIS_PORT) đang chạy. Bỏ qua Docker."
else
  ensure_docker_services
fi

# ------------------------------------------------------------------------------
# Build Frontend và Backend nếu cần
# ------------------------------------------------------------------------------
if [[ "$NO_BUILD" == false ]]; then
  if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    echo "Lỗi: Cần Node.js 22+ và npm trong PATH để build frontend." >&2
    exit 1
  fi

  MVN_EXE="$(find_maven)"

  export VITE_API_BASE_URL="/api"

  echo "Cài dependency frontend từ package-lock.json..."
  (cd "$FRONTEND_DIR" && npm ci --no-audit --no-fund) > "$RUNTIME_DIR/linux-npm-ci.log" 2>&1 || {
    echo "Lỗi: npm ci thất bại. Xem $RUNTIME_DIR/linux-npm-ci.log" >&2
    exit 1
  }

  echo "Build frontend React..."
  (cd "$FRONTEND_DIR" && npm run build) > "$RUNTIME_DIR/linux-frontend-build.log" 2>&1 || {
    echo "Lỗi: Build frontend thất bại. Xem $RUNTIME_DIR/linux-frontend-build.log" >&2
    exit 1
  }

  if [[ ! -f "$FRONTEND_DIR/dist/index.html" ]]; then
    echo "Lỗi: Frontend build chưa sinh dist/index.html." >&2
    exit 1
  fi

  rm -rf "$SOURCE_DIR/target/classes/static"

  echo "Build backend và đóng gói frontend vào JAR..."
  (cd "$SOURCE_DIR" && "$MVN_EXE" -Pwindows-web -DskipTests package) > "$RUNTIME_DIR/linux-backend-build.log" 2>&1 || {
    echo "Lỗi: Build backend thất bại. Xem $RUNTIME_DIR/linux-backend-build.log" >&2
    exit 1
  }
fi

# Kiểm tra file JAR đã tồn tại và chứa frontend
if [[ ! -f "$JAR_PATH" ]]; then
  echo "Lỗi: Không tìm thấy file $JAR_PATH. Chạy lại không có tùy chọn -NoBuild." >&2
  exit 1
fi

if ! python3 -c "import zipfile; z = zipfile.ZipFile('$JAR_PATH'); exit(0 if 'BOOT-INF/classes/static/index.html' in z.namelist() else 1)"; then
  echo "Lỗi: File JAR chưa chứa frontend đã đóng gói. Hãy chạy lại không có -NoBuild." >&2
  exit 1
fi

# Kiểm tra lại cổng lần nữa trước khi bind
assert_port_free "$APP_PORT"

# ------------------------------------------------------------------------------
# Khởi động Backend Java
# ------------------------------------------------------------------------------
JAVA_ARGS=(
  "-XX:ActiveProcessorCount=2"
  "-XX:+UseSerialGC"
  "-XX:TieredStopAtLevel=1"
  "-Xss512k"
  "-Xms32m"
  "-Xmx${HEAP_MB}m"
  "-XX:MaxMetaspaceSize=160m"
  "-XX:ReservedCodeCacheSize=64m"
  "-jar"
  "$JAR_PATH"
  "--debug=false"
  "--logging.level.org.springframework.security=INFO"
  "--spring.datasource.hikari.maximum-pool-size=5"
  "--spring.datasource.hikari.minimum-idle=1"
  "--server.port=$APP_PORT"
  "--server.address=127.0.0.1"
  "--spring.profiles.active=$ACTIVE_PROFILE"
)

if [[ -n "${DB_PASSWORD:-}" ]]; then
  JAVA_ARGS+=("--spring.datasource.password=$DB_PASSWORD")
fi

if [[ -n "${TARGET_REDIS_PORT:-}" ]]; then
  JAVA_ARGS+=("--spring.data.redis.port=$TARGET_REDIS_PORT")
fi

if [[ -n "${REDIS_PASSWORD:-}" ]]; then
  JAVA_ARGS+=("--spring.data.redis.password=$REDIS_PASSWORD")
fi

echo "Khởi động ứng dụng web/API..."
(
  cd "$SOURCE_DIR"
  setsid "$JAVA_EXE" "${JAVA_ARGS[@]}" </dev/null > "$RUNTIME_DIR/linux-app.log" 2> "$RUNTIME_DIR/linux-app-error.log" &
  echo $! > "$RUNTIME_DIR/linux-app.pid"
)
LAUNCHED_PID="$(cat "$RUNTIME_DIR/linux-app.pid")"
rm -f "$RUNTIME_DIR/linux-app.pid"
disown -a 2>/dev/null || true


STARTED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
cat <<EOF > "$STATE_PATH"
{
  "pid": $LAUNCHED_PID,
  "exePath": "$JAVA_EXE",
  "jarPath": "$JAR_PATH",
  "port": $APP_PORT,
  "profile": "$ACTIVE_PROFILE",
  "url": "$APP_URL",
  "startedAtUtc": "$STARTED_AT"
}
EOF

# Chờ ứng dụng sẵn sàng (health check)
echo "Đang chờ ứng dụng UP (tối đa 60 giây)..."
timer=0
app_ready=false
while [[ $timer -lt 60 ]]; do
  if ! kill -0 "$LAUNCHED_PID" 2>/dev/null; then
    echo "Lỗi: Tiến trình Java đã thoát bất ngờ. Xem log tại $RUNTIME_DIR/linux-app.log và linux-app-error.log" >&2
    rm -f "$STATE_PATH"
    exit 1
  fi

  if test_app_health "$APP_PORT"; then
    app_ready=true
    break
  fi

  sleep 1
  timer=$((timer + 1))
done

if [[ "$app_ready" == false ]]; then
  echo "Lỗi: Backend chưa UP sau 60 giây. Xem log tại $RUNTIME_DIR/linux-app.log và linux-app-error.log" >&2
  kill -TERM "$LAUNCHED_PID" 2>/dev/null || true
  rm -f "$STATE_PATH"
  exit 1
fi

# Kiểm tra thêm SPA endpoint
login_html="$(curl -s -m 5 "http://127.0.0.1:$APP_PORT/login" 2>/dev/null || true)"
if ! echo "$login_html" | grep -q 'id=["'\'']root["'\'']'; then
  echo "Cảnh báo: Backend UP nhưng trang /login chưa trả về SPA root template."
fi

echo "==================================================================="
echo "  KHVT ĐÃ CHẠY THÀNH CÔNG: $APP_URL"
echo "  Một tiến trình Java phục vụ cả giao diện và API."
echo "  Dùng: ./CHAY_KHVT.sh stop để dừng hệ thống."
echo "        ./CHAY_KHVT.sh status để kiểm tra trạng thái."
echo "==================================================================="

if [[ "$OPEN_BROWSER" == true ]]; then
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$APP_URL" >/dev/null 2>&1 || true
  fi
fi

exit 0
