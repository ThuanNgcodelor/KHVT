# Docker chỉ cho MySQL/Redis và deploy Cloudflare Tunnel

## Ranh giới vận hành đã chốt

Docker Compose của dự án chỉ có hai service:

1. MySQL — dữ liệu nghiệp vụ, Flyway schema.
2. Redis — Spring Session và cache ephemeral.

Không thêm API, frontend, Nginx, Adminer, cloudflared hay service tiện ích vào Compose. Java chạy native/systemd trên host; React được build và đóng trong static resources của Spring Boot để có một origin. Cloudflare Tunnel chạy trên host dưới dạng service, không chạy trong Docker.

## Cô lập với hệ thống đang có

- Đặt project name riêng, ví dụ qlmh-dev hoặc qlmh-prod; dùng cờ -p rõ ràng với lệnh compose.
- Không khai báo container_name; Docker tự namespace container/network/volume theo project.
- Tên volume riêng chỉ thuộc project này; không mount volume hiện hữu của hệ thống khác.
- Dành cổng riêng và chỉ bind localhost: development ví dụ MySQL 3307 -> 3306, Redis 6381 -> 6379. Kiểm tra cổng trống trước khi bật.
- Production cùng host: Java kết nối localhost qua cổng loopback; firewall không mở DB/cache ra Internet. Nếu app chuyển sang máy khác, phải thiết kế private network/VPN trước, không public DB port.
- Không chạy docker compose down không có project scope, không dùng down -v, không prune volume/container toàn máy. Trước thao tác luôn kiểm tra project/service cụ thể.
- Chỉ start/stop/backup tài nguyên có prefix/project của QuanLyMuaHang; không sửa service Docker ngoài phạm vi.

## Compose shape cần đạt

~~~yaml
name: qlmh-dev
services:
  mysql:
    image: mysql:8.4
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE:?MYSQL_DATABASE is required}
      MYSQL_USER: ${MYSQL_USER:?MYSQL_USER is required}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:?MYSQL_PASSWORD is required}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}
    ports:
      - "127.0.0.1:3307:3306"
    volumes:
      - qlmh_mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1"]
  redis:
    image: redis:7.4-alpine
    restart: unless-stopped
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}
    command: ["sh", "-c", "exec redis-server --requirepass \"$$REDIS_PASSWORD\" --appendonly yes"]
    ports:
      - "127.0.0.1:6381:6379"
    volumes:
      - qlmh_redis_data:/data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli -a \"$$REDIS_PASSWORD\" ping | grep -q PONG"]
volumes:
  qlmh_mysql_data:
  qlmh_redis_data:
~~~

File .env phải khai báo MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD, MYSQL_ROOT_PASSWORD và REDIS_PASSWORD. Không commit .env; không có password fallback yếu. Pin image tag đã kiểm thử; production cân nhắc pin digest.

## Development flow

~~~bash
docker compose -p qlmh-dev up -d mysql redis
mvn test
mvn spring-boot:run
cd frontend
npm ci
npm run dev
~~~

- Vite dev server chỉ bind localhost; proxy /api tới backend localhost.
- API connect tới 127.0.0.1:3307 và 127.0.0.1:6381.
- Không chạy Docker build cho API/frontend.
- Testcontainers nếu dùng chỉ tạo MySQL/Redis tạm cho test và phải tự dọn đúng container test, không thao tác vào project dev.

## Production host

- Build backend: mvn verify/package; frontend npm ci + npm run build.
- Build frontend theo task reproducible (npm ci + npm run build), đóng dist vào static resources của JAR; bật SPA history fallback cho route React nhưng không forward /api, /actuator hoặc /assets sang index.html.
- Tạo OS user riêng không phải root; deploy JAR/versioned release directory; systemd restart-on-failure, environment file ngoài repo, log rotation.
- Spring bind 127.0.0.1:8080.
- FILE_STORAGE_ROOT đặt ngoài repo và ngoài container, ví dụ /var/lib/quanlymuahang/files; quyền truy cập giới hạn service user.
- MySQL/Redis container chỉ publish loopback ports đã chọn. Compose project qlmh-prod, volume production không dùng chung local.
- Backup MySQL và file artifacts; Redis session không cần backup để bảo toàn nghiệp vụ.

## Cloudflare Tunnel

1. Mua domain, thêm zone vào Cloudflare và cấu hình DNS.
2. Tạo named tunnel; lưu credentials ngoài repository, quyền file chặt.
3. Cài cloudflared như system service trên host.
4. Ingress duy nhất cho app: domain HTTPS -> http://127.0.0.1:8080.
5. Không tạo ingress cho MySQL 3307 hoặc Redis 6381.
6. Kiểm tra HTTPS, forwarded proto, session cookie Secure, login/CSRF, file PDF download.
7. Cloudflare không thay thế VPS/host, không chạy database và không thay backup.
8. Tunnel down thì app nội bộ vẫn chạy; có health/restart alert.

## Deploy/rollback

- Release theo versioned JAR + frontend build + Flyway migration.
- Trước deploy: backup DB/files, xem migration SQL, chạy tests và import smoke test.
- systemd restart chỉ service qlmh app; không restart Docker stack toàn host.
- Rollback app bằng JAR release cũ; schema rollback bằng forward migration/restore có kiểm soát.
- Lệnh Docker phải có -p qlmh-prod; không sửa bất kỳ stack khác.

