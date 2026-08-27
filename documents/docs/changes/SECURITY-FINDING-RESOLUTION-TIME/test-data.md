# test-data

**Ticket ID**: SECURITY-FINDING-RESOLUTION-TIME
**Create date**: 2026-08-18
**Author**: Claude (QA Designer)
**Update date**: 2026-08-18

---

## Dữ liệu chung

- Dữ liệu test giả lập, không dùng ticket thật.  
- Nguồn dữ liệu: bảng `tbl_fact_security_scan`.  
- Mỗi lần quét có `scan_status` và `collected_at`.  

---

## Dữ liệu normal

| ID | data | expected |
|---|---|---|
| TD-TICKET-A | 2 bản ghi FAIL: 01/08 09:00, 02/08 10:00 | `25:00:00` |
| TD-TICKET-B | 0 bản ghi FAIL | `"-"` |
| TD-TICKET-C | 1 bản ghi FAIL duy nhất | `00:00:00` |
| TD-TICKET-D | 3 bản ghi FAIL: 01/08 09:00, 05/08 08:00, 06/08 08:00 | `119:00:00` |
| TD-TICKET-F | 1 đợt FAIL kéo dài 48h | `48:00:00` |

---

## Dữ liệu boundary

| ID | data | expected |
|---|---|---|
| TD-TICKET-G | 1 FAIL đầu, 1 FAIL cuối cách nhau 120h | `120:00:00` |
| TD-TICKET-H | 1 FAIL đầu, 1 FAIL cuối cách nhau vài giây | `00:00:03` |

---

## Dữ liệu permission

| user | role | expected |
|---|---|---|
| TD-USER-SECURITY | Có quyền Security | Xem được resolutionTime |
| TD-USER-NOACCESS | Không có quyền | Bị từ chối truy cập |

---

## Cleanup

- Tính năng chỉ đọc dữ liệu, không tạo dữ liệu mới.  
- Dữ liệu giả lập được xoá sau khi test.  

