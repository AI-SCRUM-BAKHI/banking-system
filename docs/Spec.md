# Đặc tả Risk Score cho giao dịch chuyển khoản

## 1. Phạm vi và quyết định thiết kế

Đây là đặc tả đề xuất `rule-score-v1` cho API đánh giá **một giao dịch đã phát sinh**
trên dữ liệu mô phỏng, chưa phải cơ chế chặn chuyển tiền trước khi thực hiện.
Các cửa sổ, dung sai và ngưỡng dưới đây là lựa chọn cho bản demo, cần kiểm chứng
bằng dữ liệu; không phải thông số đã được xác nhận từ source/config generator.

- Mỗi request chứa một transaction; server lấy lịch sử và baseline để đánh giá.
- Score thuộc **giao dịch hiện tại, xét tài khoản gửi**. Không cộng score của các
  event cũ không liên quan vào giao dịch mới.
- Chỉ dùng dữ liệu đã biết tại thời điểm đánh giá. Chưa đủ giao dịch hình thành
  chuỗi thì rule chuỗi chưa kích hoạt; không dùng giao dịch tương lai.
- Các giao dịch đầu chuỗi giữ kết quả online ban đầu. Nếu phân tích lại để điều
  tra, lưu thành kết quả hồi cứu riêng.
- `is_fraud` của generator là nhãn scenario để đối chiếu; `isFraud` trong API là
  dự đoán theo chính sách bên dưới, không phải xác nhận gian lận thực tế.

## 2. Đầu vào và context

### 2.1. Transaction

Các trường bắt buộc: `transactionRef`, `senderAccountId`, `receiverAccountId`,
`amountVnd`, `currency`, `transactionTime`. V1 chỉ nhận `currency=VND`, số tiền
nguyên dương và hai tài khoản khác nhau. Timestamp phải có UTC offset và được
chuẩn hóa về UTC. Nếu có `postedAt` (thời điểm ghi có), giá trị phải lớn hơn hoặc
bằng `transactionTime` và không sau thời điểm đánh giá.

Không dùng score hoặc nhãn scenario do client cung cấp để chấm điểm.

### 2.2. Context do server quản lý

- Lịch sử giao dịch thành công, gồm cả giao dịch bình thường: sender, receiver,
  amount, thời gian giao dịch và posting nếu nguồn cung cấp.
- Baseline amount và lịch sử người nhận của tài khoản gửi.
- Mốc coverage, thời gian mở tài khoản nếu có, và độ đầy đủ của dữ liệu. Không có
  bản ghi trong khoảng bị thiếu dữ liệu không đồng nghĩa không có giao dịch.
- Inbound chỉ được dùng làm nguồn tiền nếu đã posting trước outbound đang xét.

Chỉ có bảng kết quả fraud là chưa đủ. Context phải đến từ nguồn đáng tin cậy;
không chuyển lỗi truy vấn thành lịch sử rỗng. Thiếu context bắt buộc cho một rule
thì ghi `NOT_EVALUATED`, không coi là rule không khớp.

### 2.3. Thời gian và idempotency

Gọi `t` là `transactionTime` hiện tại. Cửa sổ `(t-W, t]` loại mốc đầu và gồm mốc
cuối. Chỉ dùng giao dịch đã ingest trước request này, cộng giao dịch hiện tại khi
rule yêu cầu. Giao dịch đã ingest có cùng timestamp được tính vào rolling window;
baseline và lịch sử người nhận chỉ dùng giao dịch có timestamp `< t`.

Bản demo ingest theo `transactionTime` không giảm trên toàn luồng, kể cả khi nạp
lịch sử; xử lý tuần tự/atomic để tránh hai request bỏ sót nhau. Giao dịch mới có
thời gian nhỏ hơn mốc đã xử lý bị từ chối rõ ràng. Late event/replay là phần mở
rộng; không âm thầm chấm bằng dữ liệu tương lai.

`transactionRef` là khóa idempotency, không phải feature dự đoán. Kiểm tra khóa
trước kiểm tra thứ tự thời gian: trùng khóa và cùng dữ liệu trả kết quả cũ; trùng
khóa khác dữ liệu trả conflict. Retry không tăng count, thêm lịch sử hay tạo event
mới. Lưu kết quả, lịch sử và khóa idempotency trong cùng đơn vị atomic.

## 3. Điểm và nhãn đầu ra

| Tổng điểm | `riskLevel` |
|---:|---|
| 0-29 | LOW |
| 30-59 | MEDIUM |
| 60-79 | HIGH |
| 80-100 | CRITICAL |

```text
riskScore = min(100, tổng scoreContribution sau khi loại đóng góp trùng)
fraudThreshold = 60  // mặc định cho demo, lưu cùng ruleVersion
```

Rule có điểm đóng góp, không có `riskLevel` riêng. Level suy ra từ tổng điểm.
Score 80 không có nghĩa xác suất fraud là 80%.

| Trạng thái | `isFraud` | `decision` |
|---|---|---|
| Score >= 60, kể cả còn rule chưa đánh giá được | `true` | FRAUD |
| Score < 60, mọi rule đều đánh giá được | `false` | NOT_FRAUD |
| Score < 60, còn rule chưa đánh giá được | `null` | INSUFFICIENT_DATA |

`evaluationStatus=COMPLETE` khi mọi rule đều đánh giá được, còn lại là `PARTIAL`.
Khi partial, score và level chỉ phản ánh các tín hiệu đã tính được. Chính sách
lấy max trong từng nhóm ở mục 5 bảo đảm thêm tín hiệu không làm score giảm.

Không ép thiếu lịch sử thành `false`. Nếu tích hợp bắt buộc nhận đúng boolean,
trường hợp chưa đủ dữ liệu phải trả lỗi nghiệp vụ riêng. `FRAUD` là nhãn dự đoán
phục vụ demo; quy trình review xác nhận là bước riêng.

## 4. Rule và điều kiện kích hoạt

### 4.1. Baseline dùng chung

```text
history = giao dịch gửi thành công của sender trong [t - 30 ngày, t)
baselineMedianVnd = median(history.amountVnd)
amountRatio = amountVnd / baselineMedianVnd
```

Cần coverage đầy đủ 30 ngày và ít nhất 20 mẫu; tài khoản mới hơn 30 ngày cần lịch
sử đầy đủ từ khi mở tài khoản và vẫn đủ 20 mẫu. Thiếu điều kiện thì hai rule amount
là `NOT_EVALUATED`, không thay bằng trung bình toàn hệ thống.

Median của số mẫu chẵn là trung bình hai phần tử giữa. Dùng decimal, không làm
tròn ratio trước khi so ngưỡng. Không đưa giao dịch hiện tại vào baseline, không
lấy median profile bí mật của generator hoặc lọc lịch sử theo nhãn fraud.

### 4.2. Bảng rule

| Rule | Điều kiện | Điểm |
|---|---|---:|
| `AMOUNT_SPIKE` | `amountRatio > 8` | 35 |
| `NEW_BENEFICIARY_LARGE` | `amountRatio > 8` và chưa gửi tới receiver trong `[t - 30 ngày, t)` | 50 |
| `UNUSUAL_HOUR` | Giờ theo `Asia/Ho_Chi_Minh` thuộc `[02:00, 04:00)` | 25 |
| `FREQUENCY_SPIKE` | Ít nhất 12 giao dịch gửi của sender trong `(t - 600 giây, t]`, gồm giao dịch hiện tại | 45 |
| `STRUCTURED_SPLIT` | Chín giao dịch tới receiver hiện tại thỏa mục 4.3 | 55 |
| `RAPID_PASS_THROUGH` | Outbound hiện tại và inbound gần nhất thỏa mục 4.4 | 65 |
| `MULTI_ACCOUNT_FLOW` | Inbound/outbound quanh sender thỏa mục 4.5 | 80 |

“Người nhận mới” nghĩa là **chưa xuất hiện trong 30 ngày**, không phải chưa từng
xuất hiện suốt đời tài khoản. Cần coverage 30 ngày hoặc đầy đủ từ lúc mở tài khoản;
thiếu dữ liệu thì `NOT_EVALUATED`.

Frequency cần coverage đủ 600 giây, hoặc đầy đủ từ khi mở tài khoản nếu mới hơn.
Mọi rule dùng cửa sổ lịch sử bên dưới cũng chấp nhận coverage đầy đủ từ khi mở tài
khoản nếu tuổi tài khoản ngắn hơn cửa sổ yêu cầu.

Hour dùng khung cố định cho demo, chưa học khung hoạt động riêng của tài khoản.
Đúng 02:00 được tính, đúng 04:00 không được tính. Hour đơn lẻ là 25 điểm, LOW.

### 4.3. STRUCTURED_SPLIT

Lấy chín giao dịch gần nhất của cùng cặp sender/receiver trong `(t - 3600 giây, t]`,
gồm giao dịch hiện tại; sắp theo thời gian, cùng timestamp theo thứ tự ingest.
Không tìm tập con khác để cố khớp. Điều kiện đồng thời:

- Đủ chín giao dịch, amount bằng nhau chính xác theo VND.
- Mỗi khoảng cách giữa hai giao dịch liên tiếp trong `[300, 420]` giây.
- Receiver đã có ít nhất một giao dịch từ sender trong 30 ngày **trước giao dịch
  đầu của nhóm**, không dùng các khoản trong nhóm để xác định người nhận cũ.

Cần coverage đủ cửa sổ một giờ. Có dưới chín giao dịch trong cửa sổ đầy đủ thì
không kích hoạt. Khi có chín giao dịch thỏa amount và thời gian, cần thêm coverage
để xác định người nhận cũ; thiếu phần này thì `NOT_EVALUATED`. Rule này là mẫu
chặt cho synthetic, chưa phát hiện chia nhỏ không đều hoặc amount gần bằng nhau.

### 4.4. RAPID_PASS_THROUGH

Với sender `A`, lấy inbound đã posting gần nhất vào `A` trong `[t - 120 giây, t)`;
nếu cùng posting time, chọn `transactionRef` nhỏ nhất theo thứ tự từ điển.
Chỉ kiểm tra inbound đó, không thử mọi tập con. Điều kiện:

```text
60 <= (t - inbound.postedAt) tính bằng giây <= 120
0.95 <= current.amountVnd / inbound.amountVnd <= 1.00
```

Inbound sender, `A` và outbound receiver phải là ba tài khoản khác nhau. Cần
coverage inbound đủ 120 giây và thông tin posting. Không có inbound trong cửa sổ
đầy đủ là không kích hoạt; thiếu dữ liệu posting cần thiết là `NOT_EVALUATED`.
Đây là tín hiệu về thời gian/số tiền, không chứng minh nguồn gốc chính xác của
khoản tiền trong tài khoản có số dư trộn lẫn.

### 4.5. MULTI_ACCOUNT_FLOW

Với sender `A` và outbound hiện tại:

1. `I` là **toàn bộ** inbound vào `A` có `postedAt` trong `(t - 600 giây, t)`.
2. `I` rỗng thì không kích hoạt. Ngược lại, `p` là posting time muộn nhất của `I`.
3. `O` là **toàn bộ** outbound từ `A` có `transactionTime` trong `(p, t]`, gồm
   giao dịch hiện tại.
4. Kích hoạt khi `I` có ít nhất năm sender khác nhau, `O` có ít nhất hai receiver
   khác nhau và `0.95 <= sum(O.amountVnd) / sum(I.amountVnd) <= 1.00`.

Các tập sender inbound và receiver outbound phải không giao nhau để loại mẫu
chuyển vòng đơn giản. Self-transfer đã bị loại từ đầu vào. Không chọn tập con để
đạt tỷ lệ. Cần coverage inbound/outbound đủ 10 phút và dữ liệu posting; thiếu
context cần thiết thì `NOT_EVALUATED`. Truy vấn inbound theo `transactionTime`
không thay thế được coverage theo `postedAt`.

Outbound trước hoặc bằng lần posting cuối không thuộc `O`. Giao dịch không liên
quan trong cửa sổ có thể làm rule không khớp; đây là giới hạn của bản demo.

## 5. Kết hợp rule và chống cộng trùng

Chỉ cộng match của giao dịch hiện tại; không cộng score của kết quả trước.
Mỗi rule đóng góp tối đa một lần trong response, dù có nhiều giao dịch bằng chứng.

| Nhóm | Cách lấy điểm |
|---|---|
| Amount | `max(AMOUNT_SPIKE, NEW_BENEFICIARY_LARGE)` |
| Temporal pattern | `max(FREQUENCY_SPIKE, STRUCTURED_SPLIT)` |
| Money flow | `max(RAPID_PASS_THROUGH, MULTI_ACCOUNT_FLOW)` |
| Hour | Điểm `UNUSUAL_HOUR` nếu khớp |

Cộng bốn nhóm rồi cap ở 100. Các nhóm vẫn có thể tương quan, cần calibration bằng
dữ liệu. Giữ tất cả rule khớp trong `triggeredRules`: rule thắng có đóng góp bằng
trọng số, rule bị loại có `scoreContribution=0` và `suppressedBy` chỉ ra rule thắng.
Rule chưa đánh giá được nằm trong `unevaluatedRules`, kèm lý do; khác với không khớp.

`riskEventId` do engine tạo cho từng match: hash chuẩn hóa của `ruleVersion`,
`ruleCode`, tài khoản đang đánh giá và tập `transactionRef` bằng chứng đã sắp xếp.
Match với tập bằng chứng khác nhau có thể là cửa sổ chồng lấn; **không cộng các ID
này thành score tài khoản**. Gom thành incident điều tra là bước riêng, ngoài v1.

Không dùng `is_fraud`, `fraud_type`, `fraud_event_id`, `suspicious_account_id`,
`data_split` hoặc quy luật đặt `transactionRef` để chấm điểm. Nhãn generator chỉ
được dùng sau dự đoán để đánh giá. ID được dùng cho idempotency, liên kết bằng
chứng và thứ tự ổn định, không được cộng điểm. Device, IP, location và OS chưa có
rule đóng góp điểm trong phiên bản này.

## 6. Contract API đề xuất

Endpoint mới: `POST /api/v1/transactions/evaluate`. Đây là đề xuất, chưa phải API
đã có trong repo. API ingest kết quả fraud hiện tại vẫn là luồng riêng.

Phần `data` trong envelope chung của backend, ví dụ khi context đầy đủ:

```json
{
  "transactionRef": "TX-001",
  "isFraud": true,
  "decision": "FRAUD",
  "riskScore": 95,
  "riskLevel": "CRITICAL",
  "evaluationStatus": "COMPLETE",
  "fraudThreshold": 60,
  "ruleVersion": "rule-score-v1",
  "triggeredRules": [
    {
      "ruleCode": "AMOUNT_SPIKE",
      "scoreContribution": 0,
      "suppressedBy": "NEW_BENEFICIARY_LARGE"
    },
    {
      "ruleCode": "NEW_BENEFICIARY_LARGE",
      "scoreContribution": 50
    },
    {
      "ruleCode": "FREQUENCY_SPIKE",
      "scoreContribution": 45
    }
  ],
  "unevaluatedRules": []
}
```

Response có thể chỉ trả tóm tắt như trên. Audit server phải lưu `evaluatedAt`,
version/config, độ đầy đủ và mốc context, trạng thái từng rule, feature/baseline,
`riskEventId`, transaction bằng chứng và lý do kết quả. Thay đổi ngưỡng, trọng số
hoặc điều kiện phải tạo version/config mới; retry giữ kết quả/version ban đầu.

## 7. Ví dụ và các ca cần kiểm chứng

Giả định context đầy đủ và không có rule khác kích hoạt:

| Trường hợp | Score | Level | `isFraud` |
|---|---:|---|---|
| Không có rule | 0 | LOW | false |
| Chỉ hour | 25 | LOW | false |
| Chỉ amount spike | 35 | MEDIUM | false |
| New beneficiary và amount spike | 50 | MEDIUM | false |
| Frequency và structured split | 55 | MEDIUM | false |
| Amount spike và hour | 60 | HIGH | true |
| Chỉ rapid pass-through | 65 | HIGH | true |
| Hai rule money flow cùng khớp | 80 | CRITICAL | true |
| New beneficiary và frequency | 95 | CRITICAL | true |
| Hai rule trên và hour | 100 | CRITICAL | true |

Các ca biên khi triển khai:

- Đúng `8 × median` không kích hoạt; lớn hơn mới kích hoạt.
- Baseline 19 mẫu chưa đủ; 20 mẫu và đủ coverage thì đánh giá được.
- Giao dịch thứ 11 trong burst chưa kích hoạt frequency; thứ 12 kích hoạt.
  Giao dịch đúng `t - 600 giây` bị loại khỏi cửa sổ.
- Receiver xuất hiện đúng `t - 30 ngày` là đã có lịch sử; timestamp bằng `t`
  không dùng làm lịch sử người nhận.
- Split: khoảng cách 300/420 giây được tính, 299/421 giây không được tính.
- Pass-through: tỷ lệ 0.95/1.00 và độ trễ 60/120 giây được tính; ngoài khoảng
  hoặc outbound bằng thời điểm posting không được tính.
- Multi-flow: bốn nguồn hoặc một đích không đủ; outbound trước/bằng lần posting
  cuối không thuộc `O`.
- Nhận giao dịch thứ 12 không sửa kết quả online của 11 giao dịch trước.
- Retry không tăng count; trùng ID khác dữ liệu hoặc giao dịch đến trễ trả lỗi.
- Thiếu baseline, các rule còn lại không khớp: score 0, PARTIAL, `isFraud=null`.
- Thiếu baseline nhưng pass-through khớp: score 65, PARTIAL, `isFraud=true`.
- Mốc 29/30, 59/60, 79/80 đổi level đúng bảng; tổng trên 100 được cap.

## 8. Đánh giá với dữ liệu synthetic

Nếu cấu hình có sáu event mỗi loại, số giao dịch/event lần lượt `1, 12, 1, 1, 9,
2, 7`, số giao dịch mang nhãn scenario là:

```text
6 × (1 + 12 + 1 + 1 + 9 + 2 + 7) = 198
```

Đây là phép tính theo cấu hình mô tả, chưa xác minh generator trong repo này.
Không kỳ vọng 198 giao dịch đều có `isFraud=true`: một số rule riêng lẻ dưới
ngưỡng 60, các giao dịch đầu chuỗi chưa đủ bằng chứng tại thời điểm đến.

Replay theo thời gian, chỉ dùng lịch sử đã biết. Báo cáo coverage/partial rate,
precision/recall trên dự đoán có nhãn, số ca chưa đủ dữ liệu và khả năng phát hiện
**event** bên cạnh chỉ số transaction. Không bỏ các ca partial khỏi báo cáo mà
không công bố số lượng. Không dùng nhãn scenario để chọn bằng chứng hoặc baseline.

Chưa tự động khóa tài khoản từ nhãn demo. Dùng dữ liệu thật, thay ngưỡng và chính
sách hành động cần được đánh giá riêng trước khi triển khai production.

