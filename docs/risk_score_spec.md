# Đặc tả Risk Score cho giao dịch chuyển khoản

Profile mới là `rule-score-v3-recall`, chọn bằng
`APP_FRAUD_RULE_VERSION=rule-score-v3-recall`. V1 và v2 tiếp tục có thể chọn
riêng; mặc định của code vẫn là v1. Môi trường triển khai chọn profile thực tế
qua `APP_FRAUD_RULE_VERSION`; đổi cấu hình không tự chạy lại lịch sử đã hoàn tất.

Giá trị cấu hình được hỗ trợ là `rule-score-v1`, `rule-score-v2` và
`rule-score-v3-recall`; giá trị khác làm backend khởi động thất bại.

Mục tiêu thay đổi: phát hiện nhiều giao dịch đáng ngờ hơn, chấp nhận thêm cảnh
báo nhầm để review. Không tối ưu theo Precision hoặc F1 đơn thuần. Báo cáo thêm
F2 (đặt trọng số Recall cao hơn Precision) và tỷ lệ phát hiện trên toàn bộ nhãn
fraud, kể cả những giao dịch chưa đủ dữ liệu để kết luận.

## Cấu hình tham số qua `.env`

Các tham số có thể ghi đè trong `backend/.env` hoặc environment của backend.
Backend đọc và cố định cấu hình khi khởi động; sửa `.env` cần restart. Với Docker
Compose, chạy `bash infra/deploy.sh` theo quy trình deploy của repo để tạo lại
container khi environment thay đổi, chờ readiness và reload frontend proxy. `docker compose restart` không cập nhật
các biến đã truyền qua `env_file` khi container được tạo.

Không khai báo biến thì dùng mặc định của profile v1/v2/v3 đã chọn, giữ hành vi
trước đây. Không đặt biến trống. Khối dưới là mặc định **v3-recall**; không sao chép
nguyên khối sang v1/v2 nếu muốn giữ mặc định của các profile đó.

```dotenv
APP_FRAUD_RULE_VERSION=rule-score-v3-recall
APP_FRAUD_THRESHOLD=60
APP_FRAUD_BASELINE_DAYS=30
APP_FRAUD_BASELINE_MIN_COUNT=20
APP_FRAUD_AMOUNT_SPIKE_MULTIPLIER=4
APP_FRAUD_AMOUNT_SPIKE_SCORE=60
APP_FRAUD_NEW_BENEFICIARY_MULTIPLIER=2.5
APP_FRAUD_NEW_BENEFICIARY_SCORE=65
APP_FRAUD_UNUSUAL_HOUR_START=2
APP_FRAUD_UNUSUAL_HOUR_END=4
APP_FRAUD_UNUSUAL_HOUR_SCORE=60
APP_FRAUD_FREQUENCY_WINDOW_SECONDS=120
APP_FRAUD_FREQUENCY_MIN_COUNT=2
APP_FRAUD_FREQUENCY_SCORE=65
APP_FRAUD_SPLIT_WINDOW_SECONDS=3600
APP_FRAUD_SPLIT_MIN_COUNT=2
APP_FRAUD_SPLIT_MIN_GAP_SECONDS=120
APP_FRAUD_SPLIT_MAX_GAP_SECONDS=900
APP_FRAUD_SPLIT_SCORE=70
APP_FRAUD_RAPID_MIN_DELAY_SECONDS=30
APP_FRAUD_RAPID_MAX_DELAY_SECONDS=600
APP_FRAUD_RAPID_MIN_RATIO=0.8
APP_FRAUD_RAPID_MAX_RATIO=1.2
APP_FRAUD_RAPID_SCORE=65
APP_FRAUD_MULTI_WINDOW_SECONDS=1800
APP_FRAUD_MULTI_MIN_SOURCES=3
APP_FRAUD_MULTI_MIN_TARGETS=1
APP_FRAUD_MULTI_MIN_RATIO=0.4
APP_FRAUD_MULTI_MAX_RATIO=1.2
APP_FRAUD_MULTI_SCORE=80
```

`THRESHOLD` nằm trong 1–100; điểm từng rule nằm trong 0–100 (0 chỉ bỏ đóng góp
điểm, không bỏ thực thi rule). `BASELINE_DAYS` nằm trong 1–365; số mẫu tối thiểu
nằm trong 1–100.000. Frequency/split count tối thiểu là 2; multi sources tối thiểu
là 2, targets tối thiểu là 1; các count tối đa 100.000. Cửa sổ và gap/delay nằm
trong 1–86.400 giây. Min không vượt max; cặp giờ phải có start < end, start trong
0–23 và end trong 1–24, không hỗ trợ cửa sổ qua nửa đêm. Split window phải lớn
hơn `(count−1) × minGap` do cận trái cửa sổ mở. Multiplier/ratio phải dương,
không quá 1.000, tối đa 6 chữ số thập phân và 10 chữ số có nghĩa. Cấu hình sai
làm backend không khởi động, với thông báo tên biến cần sửa.

Engine và hai repository dùng chung một cấu hình: tăng cửa sổ sẽ tăng lookback
khi query, gồm baseline trước giao dịch đầu của split. Context inbound vẫn tải
ít nhất superset 30 phút như trước; rule lọc theo cửa sổ thực tế. Coverage vẫn
phải được xác nhận đủ cho cửa sổ đã cấu hình.

Mỗi đánh giá mới lưu snapshot tất cả tham số hiệu lực trong
`audit.configuration.parameters`, cùng `ruleVersion`, weights và groups. Cùng
một profile có thể có nhiều snapshot cấu hình khác nhau; phải đối chiếu snapshot
khi so sánh kết quả. Các số benchmark ở tài liệu này dùng mặc định của profile,
không áp dụng mặc nhiên cho bộ tham số đã chỉnh.

Chưa có API/admin UI hoặc hot reload. Công thức median, grouping/suppression,
cap 100, risk levels 30/60/80, timezone Việt Nam, logic phân biệt các profile,
coverage và chống lấy dữ liệu tương lai vẫn giữ trong Java. Ví dụ giảm threshold
xuống 50 có thể tạo FRAUD với mức MEDIUM; risk level không tự đổi theo threshold.

Đổi cấu hình chỉ tác động đánh giá mới. API gọi lại cùng ref/dữ liệu vẫn trả
kết quả đã lưu; source COMPLETED không tự chạy lại. Muốn đánh giá lại cần reset
source theo quy trình vận hành, giữ lịch sử cũ và review.

## Chính sách online

Các ngưỡng dưới đây là mặc định v3-recall khi không có override.

Ngưỡng fraud giữ **60**, cap score 100, risk levels và suppression trong từng
nhóm giữ nguyên. Rule chỉ dùng thông tin đã biết tại transactionTime hiện tại.

| Rule | V3-recall |
|---|---|
| AMOUNT_SPIKE | Amount **>4× median** outbound 30 ngày; 60 điểm |
| NEW_BENEFICIARY_LARGE | Receiver chưa xuất hiện trong baseline 30 ngày, amount **>2,5× median**; 65 điểm; không bắt buộc AMOUNT_SPIKE cũng trúng |
| UNUSUAL_HOUR | Giờ Việt Nam trong `[02:00,04:00)`; **60 điểm**, có thể cảnh báo riêng lẻ |
| FREQUENCY_SPIKE | **≥2 outbound trong `(t−120s,t]`**, gồm hiện tại; 65 điểm |
| STRUCTURED_SPLIT | **2 khoản bằng tiền** gần nhất cùng sender/receiver trong một giờ, gap **120–900s**; bỏ qua khoản khác tiền xen giữa; receiver có quan hệ trong 30 ngày trước khoản đầu của cặp; 70 điểm |
| RAPID_PASS_THROUGH | Inbound hợp lệ mới nhất đã posting trước outbound **30–600s**, tỷ lệ out/in **0,8–1,2**, ba tài khoản khác nhau; 65 điểm; không yêu cầu baseline amount đủ |
| MULTI_ACCOUNT_FLOW | Toàn bộ inbound đã posting trong 30 phút; **≥3 nguồn, ≥1 đích**, hai tập không giao nhau, toàn bộ outbound sau posting cuối có tỷ lệ out/in **0,4–1,2**; 80 điểm |

AMOUNT/NEW_BENEFICIARY vẫn cần coverage/baseline hợp lệ và ít nhất 20 mẫu.
Không thay baseline bị thiếu bằng dữ liệu toàn hệ thống. Ngưỡng so sánh amount
là nghiêm ngặt `>`; ratio dùng decimal/cross multiplication, không làm tròn.

Flow cần coverage posting và thông tin posting đầy đủ cho cửa sổ tương ứng;
không tự suy ra posting time từ transaction time. Rapid tìm inbound **hợp lệ**
mới nhất, bỏ qua inbound gần hơn nhưng không thỏa delay/ratio/tài khoản. Đây là
tìm từng credit phù hợp, không tối ưu một tổ hợp tùy ý để ép tỷ lệ khớp.

Multi vẫn dùng toàn bộ dòng tiền trong cửa sổ, không chọn subset để khớp. Cửa
sổ, nguồn/đích và tỷ lệ đã nới rộng là tín hiệu review có độ nhạy cao, không
khẳng định các dòng tiền có nguồn gốc chính xác hay fraud đã được xác nhận.

Thiếu dữ liệu vẫn là NOT_EVALUATED/PARTIAL. Một tín hiệu đủ 60 điểm có thể trả
FRAUD dù partial; chưa đủ điểm và còn thiếu dữ liệu vẫn là INSUFFICIENT_DATA.
Không tự coi unknown là giao dịch an toàn.

## Online và liên kết để điều tra là hai đầu ra khác nhau

Audit của các rule temporal/flow thêm `features.patternTransactionRefs`:

- Frequency: các giao dịch thực sự trong cửa sổ burst.
- Split: hai khoản của cặp hiện tại; **không kèm các khoản baseline cũ** dùng để
  xác nhận người nhận.
- Rapid: credit phù hợp và outbound hiện tại.
- Multi: toàn bộ inbound/outbound thuộc pattern đang đánh giá.

Khi một hồ sơ có kết quả online FRAUD và rule MATCHED, script offline
`linked_investigation.py` dùng các ref này để xuất danh sách giao dịch cần điều
tra liên quan. Không dùng `audit.evidence` chung vì trường đó có thể chứa lịch
sử nền. Không dùng nhãn CSV để quyết định liên kết, không liên kết từ rule chưa
trúng, không gắn cờ cả tài khoản hoặc mọi giao dịch trong baseline.

Kết quả liên kết không sửa score, decision, lịch sử hoặc review của giao dịch
cũ; không tự tạo fraud case cho mọi giao dịch liên quan. Nó là báo cáo bao phủ
điều tra offline có nguồn evidence truy vết được, chưa phải API/job ghi ngược
kết quả hồi cứu vào production.

`known_by_source_time` là timestamp của giao dịch root đầu tiên tạo liên kết
trong replay, không phải thời điểm giao dịch sớm hơn đã được cảnh báo online,
cũng không phải timestamp triển khai thực tế. Vì vậy không trình bày tỷ lệ
liên kết hồi cứu như Recall của API evaluate tại thời điểm giao dịch phát sinh.

## Đánh giá và bật thử

Đo trên hai seed mới 20261107/20261108, tổng **29.981 giao dịch** và 396 nhãn fraud.
Baseline là v2, candidate v3 được chốt trên bộ phát triển trước khi đo holdout.

| Chỉ số | V2 online | V3 online | Liên kết offline |
|---|---:|---:|---:|
| TP | 266 | 334 | 392 |
| FP | 1 | 341 | 625 |
| FN | 128 | 60 | 4 |
| Fraud chưa kết luận | 2 | 2 | 0 |
| Phát hiện / toàn bộ nhãn fraud | 67,17% | 84,34% | 98,99% |
| Precision | 99,63% | 49,48% | 38,54% |
| Recall trên phần có quyết định | 67,51% | 84,77% | 98,99% |
| F2 trên phần có quyết định | 72,16% | 74,19% | 75,36% |

Recall = TP/(TP+FN); F2 = 5TP/(5TP+4FN+FP). Unknown được thống kê riêng,
không gán normal hoặc FN. Tỷ lệ phát hiện trên toàn bộ nhãn fraud dùng cả
unknown trong mẫu số. Các seed mới vẫn cùng generator, chưa chứng minh chất
lượng trên dữ liệu ngân hàng độc lập. Không chỉnh rule theo kết quả holdout.

```dotenv
APP_FRAUD_RULE_VERSION=rule-score-v3-recall
APP_FRAUD_SCAN_ENABLED=true
```

Không cần migration. Restart để đổi profile; API retry vẫn trả kết quả đã lưu
cho cùng ref/dữ liệu. Muốn chấm lại cần job/reset source trên database thử
nghiệm. Lịch sử version cũ và review được giữ nguyên. Database dùng cho các
benchmark là các database PostgreSQL cục bộ mới, không phải Neon production.

## Đầu vào, context và idempotency

API `POST /api/v1/transactions/evaluate` yêu cầu `X-Service-Token` và body:
transactionRef, senderAccountId, receiverAccountId, amountVnd, currency,
transactionTime, postedAt tùy chọn. VND nguyên dương, hai tài khoản khác nhau,
timestamp có UTC offset. postedAt nếu có phải từ transactionTime đến thời
điểm đánh giá, không tự gán bằng transactionTime.

`POST /api/v1/transactions` lưu source để job xử lý. Job đọc `transactions`,
tạo lịch sử mới trong `risk_transfer_history` và chỉ tạo fraud case khi
isFraud=true. `POST /api/v1/fraud-transactions` vẫn nhận kết quả đã tính từ
producer. Lưu source result/history/fraud của một lần job phải atomic; hồ sơ
và review đã tồn tại giữ nguyên.

Baseline là outbound của sender trong `[t−30 ngày,t)`, ít nhất 20 mẫu,
median chẵn là trung bình hai phần tử giữa. Cần coverage 30 ngày hoặc đầy đủ
từ thời điểm mở tài khoản nếu biết; tài khoản mới vẫn cần 20 mẫu. Không tự
xác nhận coverage vì bảng có dữ liệu. Nhãn CSV, profile ẩn của generator,
device/IP và score cũ không tham gia chấm điểm.

API lấy context từ evaluation ledger và giữ global watermark: giao dịch mới
đến muộn trả 409. API cùng ref/cùng dữ liệu trả kết quả/version đã lưu, dù
cấu hình rule đã đổi; ref cũ khác dữ liệu trả 409. Retry không thêm count/history.

Job lấy context trực tiếp từ source, không phụ thuộc processing status của
lịch sử. Loại giao dịch hiện tại, không lấy tương lai; outbound cùng timestamp
dùng thứ tự `(transaction_time, transaction_ref)`. Baseline/receiver history
chỉ dùng timestamp `<t`. Job bỏ global watermark để hỗ trợ backfill; reset
PENDING tạo lần đánh giá mới, không ghi đè lịch sử cũ.

Hai repository nạp inbound theo posting time đến 30 phút. Mỗi profile tự lọc
đúng cửa sổ; v1 vẫn chỉ dùng 10 phút cho multi, không mở rộng rule vì superset
context. Thiếu context/posting/coverage ghi NOT_EVALUATED; lỗi truy vấn không
được thay bằng lịch sử rỗng.

## Score, suppression và đầu ra

`riskScore = min(100, tổng điểm lớn nhất của mỗi nhóm)`.

| Nhóm | Điểm được cộng |
|---|---|
| Amount | max(AMOUNT_SPIKE, NEW_BENEFICIARY_LARGE) |
| Temporal | max(FREQUENCY_SPIKE, STRUCTURED_SPLIT) |
| Flow | max(RAPID_PASS_THROUGH, MULTI_ACCOUNT_FLOW) |
| Hour | UNUSUAL_HOUR nếu khớp |

Giữ mọi match trong triggeredRules. Rule bị suppression đóng góp 0 và có
suppressedBy; rule thiếu dữ liệu nằm trong unevaluatedRules. Score không phải
xác suất fraud. riskLevel: LOW 0–29, MEDIUM 30–59, HIGH 60–79, CRITICAL 80–100.

| Điều kiện | isFraud | decision |
|---|---|---|
| Score ≥60, kể cả partial | true | FRAUD |
| Score <60, mọi rule đã đánh giá | false | NOT_FRAUD |
| Score <60, còn rule thiếu dữ liệu | null | INSUFFICIENT_DATA |

evaluationStatus là COMPLETE khi mọi rule đều đánh giá được, còn lại PARTIAL.
Audit lưu evaluatedAt, version/config, coverage, feature, trạng thái từng rule,
evidence và riskEventId. ID hash version, rule code, tài khoản và tập ref
evidence đã sắp xếp; không cộng các event ID thành score tài khoản.

## Tương thích với v1 và v2

| Rule | V1 | V2 | V3-recall |
|---|---|---|---|
| Amount | >8× median; 35 điểm | >8×; 60 điểm | >4×; 60 điểm |
| New beneficiary | >8×; 50 điểm | >8×; 65 điểm | >2,5×; 65 điểm |
| Hour | 25 điểm | 25 điểm | 60 điểm |
| Frequency | ≥12/600s; 45 điểm | ≥3/120s; 65 điểm | ≥2/120s; 65 điểm |
| Split | 9 khoản, gap 300–420s; 55 điểm | 3 khoản, gap 240–480s; 70 điểm | 2 khoản bằng tiền, gap 120–900s; 70 điểm |
| Rapid | Latest credit, 60–120s, ratio 0,95–1; 65 điểm | Như v1, thêm outbound >8× median/baseline đủ; 75 điểm | Latest valid credit, 30–600s, ratio 0,8–1,2; 65 điểm |
| Multi | 600s, ≥5 nguồn/≥2 đích, ratio 0,95–1; 80 điểm | 1800s, các điều kiện khác như v1 | 1800s, ≥3 nguồn/≥1 đích, ratio 0,4–1,2; 80 điểm |

V1/v2 split lấy đúng N khoản gần nhất cùng cặp, không bỏ khoản khác tiền xen
giữa; mọi version kiểm tra quan hệ receiver trước nhóm. Giữ nguyên boundary,
coverage, suppression và ngưỡng fraud 60 của các version cũ.

## Kiểm thử

Quality gate backend đã đạt, gồm regression v1/v2, PostgreSQL integration và
15 ca unit v3 về boundary amount/time/ratio, posting/coverage thiếu, future
transaction và pattern refs tách khỏi baseline. Các script có 12 ca kiểm tra
confusion matrix, abstention, F2, ref và liên kết evidence hợp lệ.

Chạy từ root repo:

```bash
JAVA_HOME=/path/to/jdk21 bash backend/gradlew -p backend qualityGate
python3 -m unittest discover -s backend/scripts/evaluation-benchmark -p 'test_*.py' -q
```

PostgreSQL integration cần SCAN_TEST_DATABASE_URL trỏ vào database thử nghiệm
và SCAN_TEST_DATABASE_USER/PASSWORD tương ứng. Benchmark runner chỉ chấp nhận
localhost, dùng database mới cho mỗi run; không nạp nhãn hoặc score vào engine.
