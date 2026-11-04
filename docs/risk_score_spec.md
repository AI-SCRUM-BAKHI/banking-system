# Đặc tả Risk Score cho giao dịch chuyển khoản

## 1. Phạm vi và cách hiểu nhãn

Tài liệu này là đặc tả **risk score đề xuất** cho dữ liệu synthetic hiện tại. Generator
chưa tính cột `risk_score`; `is_fraud=1` chỉ cho biết giao dịch thuộc một scenario được
chèn có chủ đích, không phải kết luận gian lận thực tế.

Risk score nên được tính theo **event/tài khoản**, sau đó gán lại cho từng giao dịch
trong event. Cách này tránh cộng điểm 12 lần cho cùng một `FREQUENCY_SPIKE` hoặc
9 lần cho cùng một `STRUCTURED_SPLIT`.

## 2. Thang điểm và ngưỡng đề xuất

| Tổng điểm | Mức rủi ro | Hành động gợi ý |
|---:|---|---|
| 0-29 | LOW | Cho phép, tiếp tục theo dõi |
| 30-59 | MEDIUM | Tăng giám sát, yêu cầu kiểm tra bổ sung tùy chính sách |
| 60-79 | HIGH | Đưa vào hàng đợi review/hold theo chính sách |
| 80-100 | CRITICAL | Escalate ngay; không tự động kết luận là fraud |

Điểm được giới hạn trong khoảng `0-100`:

```text
risk_score = min(100, tổng điểm các rule đang kích hoạt)
```

Nên lưu cả `risk_score`, `risk_level`, danh sách `triggered_rules` và
`risk_event_id` để giải thích quyết định.

## 3. Rule và trọng số

| Rule | Điều kiện theo generator hiện tại | Điểm | Mức |
|---|---|---:|---|
| `AMOUNT_SPIKE` | Số tiền lớn hơn `8x` median amount của tài khoản; config hiện tại sinh khoảng `12x-18x` | 35 | HIGH |
| `FREQUENCY_SPIKE` | `12` giao dịch trong `11 x 39 = 429` giây, tức dưới 10 phút | 45 | HIGH |
| `UNUSUAL_HOUR` | Giao dịch vào 02:00 hoặc 03:00, ngoài khung hoạt động bình thường | 25 | MEDIUM |
| `NEW_BENEFICIARY_LARGE` | Người nhận chưa từng xuất hiện trước đó và số tiền lớn hơn `8x` median; config sinh khoảng `15x-22x` | 50 | HIGH |
| `STRUCTURED_SPLIT` | `9` khoản bằng nhau tới cùng người nhận cũ, cách nhau `6` phút | 55 | HIGH |
| `RAPID_PASS_THROUGH` | Nhận tiền rồi chuyển tiếp khoảng `98%` sau `90` giây | 65 | HIGH |
| `MULTI_ACCOUNT_FLOW` | `5` nguồn chuyển vào một tài khoản trung gian rồi tài khoản đó chuyển tới `2` đích; forward khoảng `98%` tổng tiền | 80 | CRITICAL |

Các điểm trên là điểm cho **một event**. Nếu nhiều event độc lập của cùng tài khoản
xảy ra trong cửa sổ đánh giá, cộng điểm nhưng vẫn cap ở `100`.

## 4. Quy tắc kết hợp và chống double-counting

1. Một event chỉ được tính điểm một lần, dùng `fraud_event_id` hoặc khóa
   `(suspicious_account_id, fraud_type, thời gian event)`.
2. Các giao dịch trong `RAPID_PASS_THROUGH` và `MULTI_ACCOUNT_FLOW` đều nhận cùng
   điểm event; không cộng riêng inbound/outbound.
3. Nếu pipeline không có nhãn scenario, có thể phát hiện rule từ feature giao dịch:
   - `txn_count_last_1h`, `txn_count_last_24h`;
   - `total_amount_last_1h_vnd`, `user_avg_txn_amt_vnd`;
   - `time_since_last_transaction_min`, `txn_hour`;
   - lịch sử người nhận theo `sender_account_id` và `receiver_account_id`;
   - đồ thị inbound/outbound trong cửa sổ thời gian.
4. Không dùng các cột sau làm feature dự đoán: `is_fraud`, `fraud_type`,
   `fraud_event_id`, `suspicious_account_id`, `data_split`, `transaction_id`.
   Đây là nhãn/metadata của generator và gây leakage.
5. `device_hash`, IP, location và OS chỉ là tín hiệu bổ trợ. Generator hiện tại không
   định nghĩa scenario fraud dựa riêng trên thiết bị, IP hoặc địa điểm.

## 5. Công thức feature gợi ý

### 5.1. Bất thường về số tiền

```text
amount_ratio = amount_vnd / user_avg_txn_amt_vnd
```

Ưu tiên median hoặc baseline theo tài khoản thay vì trung bình toàn hệ thống. Với dữ
liệu hiện tại, `AMOUNT_SPIKE` và `NEW_BENEFICIARY_LARGE` được xác nhận bằng
median nội bộ của profile tài khoản.

### 5.2. Bất thường về tần suất

Đếm số giao dịch **gửi trước giao dịch hiện tại** trong rolling window. Rule
`FREQUENCY_SPIKE` cần kiểm tra cả số lượng và thời lượng:

```text
count >= 12 AND (last_timestamp - first_timestamp) < 600 seconds
```

Không chỉ dùng `txn_count_last_1h`, vì feature này không chứng minh được các giao
dịch nằm trong cùng một burst dưới 10 phút.

### 5.3. Bất thường về thời gian

So sánh `txn_hour` với khung hoạt động riêng của tài khoản. Config nền cho phép lệch
giờ hiếm (`off_hours_probability=0.015`), vì vậy giao dịch ngoài giờ đơn lẻ chỉ nên
ở mức điểm thấp/trung bình và cần thêm tín hiệu khác trước khi escalate.

### 5.4. Luồng tiền

Đối với pass-through:

```text
outgoing_amount / incoming_amount ≈ 0.98
outgoing_time - incoming_posting_time ≈ 90 seconds
```

Đối với multi-account flow, kiểm tra `5` sender khác nhau cùng chuyển vào một tài
khoản, sau đó tiền được chuyển tới `2` receiver khác nhau và các khoản outbound chỉ
xảy ra sau khi inbound đã posting.

## 6. Ví dụ với cấu hình hiện tại

Với `scenario_counts=6` cho mỗi loại, generator tạo:

```text
6 × (1 + 12 + 1 + 1 + 9 + 2 + 7) = 198 positive transactions
```

`198` là số giao dịch được gán nhãn scenario, **không phải** tổng số giao dịch có
`risk_score >= 80`. Ví dụ:

- Một event `UNUSUAL_HOUR` độc lập: `25` điểm, mức `MEDIUM`.
- Một event `NEW_BENEFICIARY_LARGE` cộng với `FREQUENCY_SPIKE`: `95` điểm, mức
  `CRITICAL` sau khi cap.
- Một event `MULTI_ACCOUNT_FLOW`: `80` điểm, mức `CRITICAL`.

## 7. Giới hạn sử dụng

- Đây là rule score cho dữ liệu mô phỏng, cần calibration trên dữ liệu thật đã được
  ẩn danh và đánh giá độc lập trước khi dùng trong production.
- `is_fraud=0` không chứng minh giao dịch an toàn; giao dịch nền có thể tình cờ giống
  một rule.
- Không tự động khóa tài khoản hoặc kết luận fraud chỉ từ score; cần workflow review,
  ngưỡng theo sản phẩm và yêu cầu tuân thủ tương ứng.
- Với cấu hình mặc định, fraud chỉ được chèn trong 7 ngày test. Nếu dùng để train
  supervised ML, cần cân nhắc `injection_period=train_and_test` và đánh giá theo
  thời gian/account-held-out để hạn chế leakage.
