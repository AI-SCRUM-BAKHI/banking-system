# Kết quả kiểm thử API backend — 04/11/2026

Server: `http://52.63.199.66:8080`. Swagger UI và `/v3/api-docs` truy cập thành công (HTTP 200).
Kiểm thử bằng HTTP trực tiếp theo OpenAPI đang được Swagger sử dụng; không tự động bấm giao diện trình duyệt.

Kết quả: **23/23 API có phản hồi thành công; 61/61 ca kiểm tra cuối cùng đạt**. Bao gồm nhánh review FALSE_POSITIVE và các trường hợp bị từ chối đúng mã lỗi.

## Phạm vi API

| API | Kết quả thành công thực tế |
|---|---|
| `GET /api/v1/users` | 200 |
| `POST /api/v1/users` | 201 |
| `POST /api/v1/transactions` | 201 |
| `POST /api/v1/transactions/evaluate` | 200 |
| `GET /api/v1/fraud-transactions` | 200 |
| `POST /api/v1/fraud-transactions` | 201 |
| `POST /api/v1/auth/register` | 200 |
| `POST /api/v1/auth/refresh` | 200 |
| `POST /api/v1/auth/logout` | 200 |
| `POST /api/v1/auth/login` | 200 |
| `GET /api/v1/users/{id}` | 200 |
| `PATCH /api/v1/users/{id}` | 200 |
| `PATCH /api/v1/users/{id}/status` | 200 |
| `PATCH /api/v1/users/{id}/password` | 200 |
| `PATCH /api/v1/users/me/password` | 200 |
| `PATCH /api/v1/fraud-transactions/{id}/review-status` | 200 |
| `GET /api/v1/system/ping` | 200 |
| `GET /api/v1/fraud-transactions/{id}` | 200 |
| `GET /api/v1/fraud-transactions/{id}/rule-hits` | 200 |
| `GET /api/v1/fraud-transactions/{id}/graph` | 200 |
| `GET /api/v1/fraud-transactions/summary` | 200 |
| `GET /api/v1/fraud-transactions/ip-intelligence` | 200 |
| `GET /api/v1/auth/me` | 200 |

## Các luồng đã xác minh

- Auth: đăng ký, đăng nhập, sai mật khẩu, duplicate username, refresh rotation, chống dùng lại refresh token, logout và current user.
- Users: tạo/đọc/sửa, phân trang, quyền ADMIN, khóa/mở tài khoản, đổi mật khẩu, admin reset mật khẩu; xác nhận mật khẩu cũ bị từ chối và mật khẩu mới đăng nhập được.
- Fraud: tạo hồ sơ bằng service token, duplicate 409, đọc danh sách/lọc/chi tiết/rule hits/graph, summary và IP intelligence. IP intelligence trả 11.764 giao dịch có IP.
- Review: NEW → REVIEWING → CONFIRMED_FRAUD; REVIEWING → FALSE_POSITIVE; chuyển khỏi trạng thái kết thúc bị chặn 409.
- Source transaction: POST trả 201/PENDING, duplicate 409, amount=0 bị chặn 400, thiếu service token bị chặn 401.
- Evaluate: thiếu coverage trả PARTIAL/INSUFFICIENT_DATA/isFraud=null; retry cùng dữ liệu trả nguyên kết quả; đổi payload cùng ref và giao dịch đến muộn bị chặn 409; amount=0 bị chặn 400.

## Điểm cần lưu ý

- APP_SERVICE_TOKEN trong backend/.env tại máy local bị EC2 từ chối 401. Token người dùng cung cấp sau đó đã được chấp nhận. Không sửa file .env hoặc cấu hình server trong lượt kiểm thử này.
- Swagger đang mô tả response 200 cho POST /users, POST /transactions và POST /fraud-transactions; thực tế ba API tạo này trả 201, phù hợp code controller. Đây là chênh lệch tài liệu, chưa chỉnh code trong lượt kiểm thử.
- Job background không phải API Swagger. Lượt này kiểm tra enqueue PENDING và evaluate riêng, không bật scanner hoặc xác nhận một chu kỳ job tự chạy trên production.
- Đây là kiểm thử chức năng, không phải kiểm thử tải hoặc kiểm toán bảo mật toàn diện.

## Chuẩn bị và dọn dữ liệu

Tạo user kiểm thử riêng; tạm gán ADMIN cho đúng user này bằng SQL để kiểm tra nhóm quản trị. Các lượt kiểm tra đọc/review ban đầu dùng fixture SQL riêng khi service token chưa hợp lệ; lượt cuối đã tạo fraud qua HTTP thành công. Đã xóa toàn bộ fixture/user/refresh token liên quan sau kiểm thử. Token tạm không được lưu vào repository hoặc báo cáo.
Đối chiếu fingerprint trước/sau cho giao dịch nguồn, raw CSV, nhãn, khách hàng, mapping, lịch sử đánh giá, hồ sơ fraud, rule hits và chain: tất cả giữ nguyên. Global transaction-time watermark giữ nguyên. Bộ đếm ingest_order đã tăng theo lần đánh giá thử; khoảng trống sau cleanup là hợp lệ, không reset bộ đếm.

| Bảng | Trước | Sau |
|---|---:|---:|
| `users` | 10 | 10 |
| `transactions` | 11765 | 11765 |
| `risk_transfer_history` | 11768 | 11768 |
| `fraud_transactions` | 200 | 200 |
| `fraud_rule_hits` | 277 | 277 |
| `transaction_chain` | 1688 | 1688 |
| `customers` | 120 | 120 |

## Chi tiết các ca kiểm tra cuối cùng

| Ca kiểm tra | HTTP thực tế | Kết quả |
|---|---:|---|
| Public health | 200 | PASS |
| Register test user | 200 | PASS |
| Duplicate username | 409 | PASS |
| Invalid registration | 400 | PASS |
| Bank user cannot manage users | 403 | PASS |
| No bearer token | 401 | PASS |
| Invalid bearer token | 401 | PASS |
| Wrong login password | 401 | PASS |
| Login test admin | 200 | PASS |
| Current admin identity | 200 | PASS |
| List users | 200 | PASS |
| Invalid user pagination | 400 | PASS |
| Create managed bank user | 201 | PASS |
| Duplicate managed user | 409 | PASS |
| Get managed user | 200 | PASS |
| Unknown user | 404 | PASS |
| Update managed user | 200 | PASS |
| Disable managed user | 200 | PASS |
| Disabled user login | 401 | PASS |
| Enable managed user | 200 | PASS |
| Login bank user | 200 | PASS |
| Change own password | 200 | PASS |
| Old password rejected | 401 | PASS |
| New password accepted | 200 | PASS |
| Admin reset password | 200 | PASS |
| Reset password accepted | 200 | PASS |
| Rotate refresh token | 200 | PASS |
| Rotated refresh replay rejected | 401 | PASS |
| Fraud ingest requires service token | 401 | PASS |
| Create fraud fixture | 201 | PASS |
| Duplicate fraud fixture | 409 | PASS |
| Invalid fraud payload | 400 | PASS |
| Bank user lists fraud | 200 | PASS |
| Fraud filters | 200 | PASS |
| Get fraud fixture | 200 | PASS |
| Get rule hits | 200 | PASS |
| Get transaction graph | 200 | PASS |
| Review REVIEWING | 200 | PASS |
| Review CONFIRMED_FRAUD | 200 | PASS |
| Dashboard summary | 200 | PASS |
| IP intelligence | 200 | PASS |
| Unknown fraud id | 404 | PASS |
| Invalid review status | 400 | PASS |
| Source ingest requires service token | 401 | PASS |
| Enqueue source transaction | 201 | PASS |
| Duplicate source transaction | 409 | PASS |
| Source amount zero | 400 | PASS |
| Evaluate requires service token | 401 | PASS |
| Evaluate missing coverage | 200 | PASS |
| Idempotent evaluation retry | 200 | PASS |
| Changed evaluation retry | 409 | PASS |
| Invalid evaluation amount | 400 | PASS |
| Late evaluation rejected by legacy API | 409 | PASS |
| Logout refresh session | 200 | PASS |
| Logged out refresh rejected | 401 | PASS |
| Register terminal review tester | 200 | PASS |
| Review FALSE_POSITIVE from REVIEWING | 200 | PASS |
| Terminal review cannot reset to NEW | 409 | PASS |
| Terminal review cannot change decision | 409 | PASS |
| Review requires bearer token | 401 | PASS |
| Logout terminal reviewer | 200 | PASS |
