# QuanLyMuaHang source skeleton

Khung ban đầu cho Java 21 + Spring Boot REST + React/Vite/Tailwind + JPA + Flyway + MySQL.

## Cấu trúc đã có

- `pom.xml`: Maven dependencies.
- `src/main/java`: application, model JPA, repository mẫu, normalizer, dashboard controller.
- `src/main/resources/db/migration/V1__init.sql`: schema nền.
- `frontend/src/config/baseApi.ts`: cấu hình duy nhất cho `VITE_API_BASE_URL`.
- `frontend/src`: React/Vite dashboard, màn hình mua hàng, tra cứu giá, import và nhân sự.
- `src/test/java`: context test, normalizer test, model test.
- `Dockerfile`, `frontend/Dockerfile`, `docker-compose.yml`, `.env.example`.

## Chạy local khi đã cài Java 21 và Maven

```bash
cp .env.example .env
mvn test
mvn spring-boot:run

# Terminal khác
cd frontend
npm install
npm run dev
```

Mở `http://localhost:5173/`. API mặc định chạy tại `http://localhost:8080/api`.

## Chạy bằng Docker

```bash
cp .env.example .env
docker compose up -d --build
docker compose logs -f app
```

Mở `http://localhost:3000/`. Nginx frontend proxy `/api` vào backend; MySQL không nên public trên production.

## Đổi địa chỉ API

Chỉ chỉnh `frontend/.env` (logic dùng cấu hình tại `src/config/baseApi.ts`):

```text
VITE_API_BASE_URL=https://api.example.com/api
```

Mặc định là `/api`, phù hợp khi frontend Nginx và backend đi cùng domain. Không hard-code URL API trong component.

## Model hiện tại

- `Supplier`
- `Material`
- `HistoricalPurchase`
- `PurchaseOrder`
- `PurchaseOrderItem`
- `ImportBatch`
- Enum tiền tệ, loại vật tư, trạng thái PO/import.

Đây mới là skeleton có thể mở rộng theo [plan](../plan/README.md), chưa phải toàn bộ chức năng nghiệp vụ.
