# Import CSV vào Neon — schema hợp nhất V9

Script bảo toàn ba CSV và đưa giao dịch nguồn vào hàng đợi. Nhãn giả lập được giữ
riêng với điểm engine dưới cột `transactions.source_label`, không dùng để gán riskScore.

## Các bảng sau hợp nhất

| Bảng | Vai trò |
| --- | --- |
| `customers` | Khách hàng, khóa `customer_id` |
| `account_customer_mapping` | Tài khoản thuộc khách hàng, tên và ngân hàng theo CSV |
| `transactions` | Giao dịch nguồn, `customer_id`, `reference_no`, nguyên CSV `source_data`, nhãn `source_label` |
| `risk_transfer_history` | Lịch sử đánh giá dùng chung của API evaluate và job; mỗi lần đánh giá là một dòng |
| `dataset_import_runs` | Biên bản import và SHA-256 của ba file |

V8 chuyển dữ liệu từ các bảng mới bị chồng chức năng vào bảng chính. V9 kiểm tra
copy đầy đủ trước khi bỏ `transaction_source_data`, `transaction_source_labels`,
`transaction_evaluations`. Các kết quả/audit cũ, hồ sơ fraud và review được giữ nguyên.
Giao dịch đã có kết quả phù hợp trong ledger được cập nhật `COMPLETED` để tránh chấm
lại chỉ do thay schema. Một giao dịch có nhiều lần đánh giá; query ledger cần chọn
lần có `ingest_order` lớn nhất. Engine không đếm nhiều lần cùng một giao dịch.

## Bộ bàn giao

```text
neon-dataset-upload/
  README.vi.md
  import_dataset.py
  requirements.txt
  test_import_dataset.py
  verify_import.sql
  MANIFEST.json
  migrations/    # V1–V9, giữ nguyên các migration cũ
  data/
    account_customer_mapping.csv
    transactions_completed.csv
    transaction_labels.csv
```

Bộ dữ liệu hiện tại: 120 khách hàng, 120 tài khoản, 11.764 giao dịch, 11.764 nhãn
(198 nhãn giả lập dương), thời gian giao dịch 28/08–02/11/2026 theo `+07:00`.
Script đọc UTF-8 có hoặc không BOM, kiểm tra mã trùng, mapping/tên/ngân hàng, số
tiền VND nguyên dương, timezone/posting time và labels khớp 1:1 trước khi kết nối.
Nội dung 37 cột CSV được giữ trong JSONB, kể cả trường rỗng.

## 1. Chuẩn bị và migration

- Kiểm tra đúng project/branch/database; thử trên branch riêng trước production.
- Tắt scanner trên mọi instance (`APP_FRAUD_SCAN_ENABLED=false`) và restart các
  instance trong lúc chuyển schema. Dừng các writer/importer khác nếu có thể.
- Dùng direct URL, hostname không có `-pooler`, có SSL. Không ghi URL/token vào repo.
- Triển khai backend mới có V1–V9. **Code cũ của scanner không tương thích sau V9**:
  cần rollout code và schema cùng nhau; API evaluate cũ và dữ liệu lịch sử vẫn được giữ.
- Tạo backup/branch trước khi contract V9. Flyway tự kiểm tra checksum V1–V7;
  không `repair`, `baseline`, `clean`, sửa migration cũ hay dùng `DROP ... CASCADE`.

Backend mới tự chạy Flyway khi startup. Nếu admin dùng Flyway CLI hỗ trợ PostgreSQL,
chạy tại thư mục gói đã giải nén:

```bash
export FLYWAY_URL='jdbc:postgresql://DIRECT_HOST:5432/neondb?sslmode=require'
export FLYWAY_USER='neondb_owner'
read -rsp 'Database password: ' FLYWAY_PASSWORD; echo
export FLYWAY_PASSWORD
export FLYWAY_LOCATIONS='filesystem:./migrations'
export FLYWAY_TARGET='9'
flyway -ignoreMigrationPatterns='*:pending' validate
flyway migrate
unset FLYWAY_PASSWORD
```

Chỉ trạng thái migration mới đang chờ (`pending`) được chấp nhận. Checksum mismatch
hoặc dữ liệu copy không khớp phải được đối chiếu trước, không bỏ qua lỗi.

## 2. Chạy kiểm tra offline

Cần Python 3.10+. Linux/macOS:

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install -r requirements.txt
python import_dataset.py --data-dir ./data
```

Windows PowerShell:

```powershell
py -3 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe import_dataset.py --data-dir .\data
```

Không có `--check-db` hay `--apply` thì lệnh không mở kết nối database. Khi chạy
từ repo root, dùng `python backend/scripts/neon-import/import_dataset.py --data-dir ../output`.

## 3. Preflight và upload

Nhập direct PostgreSQL URI còn hiệu lực (không có tiền tố `jdbc:`) vào biến riêng;
không sửa `.env` của ứng dụng. Linux/macOS:

```bash
read -rsp 'Neon direct URL: ' NEON_IMPORT_DATABASE_URL; echo
export NEON_IMPORT_DATABASE_URL
python import_dataset.py --data-dir ./data --check-db
python import_dataset.py --data-dir ./data --apply
unset NEON_IMPORT_DATABASE_URL
```

Windows PowerShell:

```powershell
$neonImportSecret = Read-Host 'Neon direct URL' -AsSecureString
$env:NEON_IMPORT_DATABASE_URL = [System.Net.NetworkCredential]::new('', $neonImportSecret).Password
.\.venv\Scripts\python.exe import_dataset.py --data-dir .\data --check-db
.\.venv\Scripts\python.exe import_dataset.py --data-dir .\data --apply
Remove-Item Env:NEON_IMPORT_DATABASE_URL
```

Preflight chỉ đọc schema. Upload dùng COPY vào bảng tạm, kiểm tra xung đột, thêm
customers → mapping → transactions và biên bản import trong **một transaction**.
Scanner/importer cùng dùng advisory lock; các table lock chặn writer khác trong
lượt import. Tài khoản cần SELECT/INSERT, tạo bảng tạm và lấy khóa trên các bảng đích.

Với database chưa có bộ dữ liệu này, kết quả thêm mới là:

```json
{"customers":120,"account_customer_mapping":120,"transactions":11764}
```

Phải thấy `"upload": "COMMITTED"`. Chạy lại cùng nội dung bỏ qua bản ghi giống hệt,
không reset điểm/trạng thái/review. Cùng mã nhưng khác nội dung thì rollback toàn
bộ; không có chế độ ghi đè. Nếu mất kết nối đúng lúc COMMIT, kiểm tra biên bản hoặc
chạy lại cùng file để xác nhận, không mặc định dữ liệu chưa commit.

## 4. Kiểm tra kết quả và chạy job

Chạy `verify_import.sql` trong Neon SQL Editor hoặc:

```bash
psql "$NEON_IMPORT_DATABASE_URL" -v ON_ERROR_STOP=1 -f verify_import.sql
```

Trong lệnh trên cần đặt lại biến URL nếu đã unset sau upload. Tổng số dòng có thể
lớn hơn 11.764 nếu database có giao dịch test hoặc dataset khác. Dữ liệu khách hàng
của POST mới được tra từ tài khoản gửi; nếu chưa có mapping thì `customer_id` có thể NULL.

Importer không tạo coverage và không gọi engine. Operator phải xác nhận lịch sử
đầy đủ trước khi chứng nhận `risk_account_coverage`. Bảng này dùng chung với API
evaluate tuần tự cũ; phải xác nhận đúng nguồn lịch sử của từng luồng. Không lấy
min/max timestamp hoặc labels làm bằng chứng đầy đủ.

Sau khi xác nhận, bật `APP_FRAUD_SCAN_ENABLED=true` rồi restart backend. Scanner
cần direct connection hoặc session pooling, không dùng transaction pooler của
Neon vì có session advisory lock. Job mặc định tối đa 100 giao dịch mỗi lượt,
delay 30 giây. Chỉ các dòng PENDING/RETRY được xử lý; lịch sử đã chuyển không tự
được đánh giá lại. Reset phải có chọn lọc theo hướng dẫn trong `docs/transaction-scan.md`.

198 nhãn giả lập không đồng nghĩa 198 kết quả engine. Nhãn CSV và quyết định review
được giữ độc lập. Trang IP đọc ledger mới nhất và ưu tiên metadata đã gộp trong
`transactions`, fallback audit cũ để giữ dữ liệu kiểm thử trước đây.

## 5. Test importer

```bash
python -m unittest discover -s . -p 'test_import_dataset.py' -v
```

Các test PostgreSQL chỉ chạy khi đặt `NEON_IMPORT_TEST_DATABASE_URL` tới một
PostgreSQL **test cục bộ**, có quyền CREATE DATABASE. Chúng tạo/xóa database tên
ngẫu nhiên `neon_import_test_*`, kiểm tra V1–V9, chuyển dữ liệu cũ, rollback,
guard trước khi xóa bảng, khóa và chạy lại. Không dùng URL production để chạy test.

Tham khảo: https://neon.com/docs/connect/connection-pooling và
https://www.psycopg.org/psycopg3/docs/basic/copy.html.
