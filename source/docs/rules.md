# LUẬT CHƠI VÀ QUY TẮC NGHIỆP VỤ

> Tài liệu này là nguồn sự thật duy nhất về luật chơi. Server, client Socket (desktop) và client Web
> đều phải tuân theo. Mọi thay đổi luật phải cập nhật file này **trước**, sau đó mới sửa code.
> Các giá trị thời gian có thể cấu hình trong `source/server/src/main/resources/application.yml` (mục `caro.*`).

## 1. Bàn cờ và nước đi

| Quy tắc | Giá trị |
|---|---|
| Kích thước bàn cờ | 15 × 15, tọa độ `x` (cột) và `y` (hàng) chạy từ `0` đến `14`, gốc `(0,0)` ở góc trên bên trái |
| Số người chơi | 2: một người cầm `X`, một người cầm `O` |
| Đi trước | `X` |
| Điều kiện thắng | Có **từ 5 quân liên tiếp trở lên** cùng loại theo hàng ngang, hàng dọc hoặc một trong hai đường chéo |
| Luật chặn hai đầu | **Không áp dụng** (5 quân bị chặn hai đầu vẫn thắng) |
| Hòa | Bàn cờ kín (225 nước) mà không ai thắng |

Một nước đi hợp lệ khi **tất cả** điều kiện sau đúng:
1. Người gửi đã đăng nhập và là người chơi của trận đó (`matchId` đúng).
2. Trận đang ở trạng thái `IN_PROGRESS` (chưa kết thúc, không bị tạm dừng do đối thủ mất kết nối).
3. Đang đến lượt của người gửi.
4. Tọa độ nằm trong bàn cờ và ô đó còn trống.

**Server là nơi duy nhất quyết định** nước đi hợp lệ, kết quả trận và điểm số. Client chỉ hiển thị
những gì server gửi về và không tự đặt quân hay tự kết luận thắng thua.

## 2. Phòng và trận đấu

- Mỗi người chơi chỉ ở **tối đa một phòng** tại một thời điểm.
- Phòng có tối đa 2 người. Người tạo phòng là chủ phòng (host).
- Khi người thứ hai vào phòng, **trận đấu tự động bắt đầu**.
- Người tạo phòng cầm `X` ở ván đầu. Khi chơi lại (rematch), hai người đổi quân cho nhau.
- Trạng thái phòng:
  - `WAITING`: có 1 người, đang chờ đối thủ.
  - `PLAYING`: có 2 người và trận đang diễn ra.
  - `FINISHED`: trận vừa kết thúc, hai người vẫn ở trong phòng.
- Khi một người rời phòng ở trạng thái `FINISHED`, người còn lại trở thành chủ phòng và phòng quay về `WAITING`.
- Phòng không còn ai sẽ bị xóa.

## 3. Thời gian, đầu hàng, rời phòng

| Tình huống | Xử lý |
|---|---|
| Mỗi lượt đi | Tối đa **30 giây**. Hết giờ thì người đang tới lượt **thua** (lý do `TIMEOUT`) |
| Đầu hàng | Người đầu hàng **thua ngay** (lý do `RESIGN`) |
| Rời phòng hoặc đăng xuất khi trận đang diễn ra | Tính như **đầu hàng** |

## 4. Kết nối, heartbeat và kết nối lại

| Quy tắc | Giá trị |
|---|---|
| Client gửi `PING` | Mỗi **10 giây** |
| Coi là mất kết nối | Server không nhận được thông điệp nào (kể cả `PING`) trong **30 giây**, hoặc kết nối bị đóng hay lỗi |
| Thời gian chờ kết nối lại | **30 giây** |

Khi một người chơi mất kết nối trong lúc trận đang diễn ra:
1. Server **tạm dừng đồng hồ lượt** và lưu lại thời gian còn lại của lượt hiện tại.
2. Đối thủ nhận được `OPPONENT_DISCONNECTED`, kèm thời điểm hết hạn chờ.
3. Nếu người đó đăng nhập lại trong 30 giây, server gửi lại toàn bộ trạng thái trận (`MATCH_STATE`),
   đồng hồ lượt tiếp tục với thời gian còn lại, và đối thủ nhận được `OPPONENT_RECONNECTED`.
4. Nếu quá 30 giây, người mất kết nối **thua** (lý do `DISCONNECT`).
5. Nếu **cả hai** cùng mất kết nối, trận bị **hủy** (`ABORTED`, lý do `BOTH_DISCONNECTED`) và **không tính điểm**.

Người chơi đang ở phòng `WAITING` hoặc `FINISHED` mà mất kết nối sẽ được tự động rời phòng.

## 5. Phiên đăng nhập

- **Mỗi tài khoản chỉ có một phiên hoạt động.** Đăng nhập ở nơi khác (Socket hay Web đều được) sẽ khiến
  phiên cũ nhận `KICKED` (lý do `LOGGED_IN_ELSEWHERE`) rồi bị đóng.
- Đây cũng là cơ chế kết nối lại: phiên mới được gắn ngay vào trận đang chơi.
- Tài khoản bị khóa (`LOCKED`) không đăng nhập được. Nếu đang online sẽ nhận `KICKED` (lý do `ACCOUNT_LOCKED`)
  rồi bị xử lý như mất kết nối.

## 6. Điểm xếp hạng (Elo)

- Điểm khởi đầu: **1000**. Hệ số `K = 32`.
- Điểm kỳ vọng của X: `E_x = 1 / (1 + 10^((R_o - R_x) / 400))`.
- Điểm thực tế: thắng = 1, hòa = 0.5, thua = 0.
- `Δx = round(K × (S_x − E_x))`, `Δo = −Δx` (tổng điểm hai bên không đổi).
- Điểm được tính từ rating của hai người **tại thời điểm trận bắt đầu**.
- Trận `ABORTED` không làm thay đổi điểm.

## 7. Lưu trữ

- Mỗi trận được lưu **đúng một lần** khi kết thúc: một dòng trong bảng `matches` (khóa chính là `matchId`)
  cùng toàn bộ nước đi trong bảng `match_moves`.
- Trạng thái phòng và trận đang diễn ra chỉ nằm trong bộ nhớ server, không ghi vào DB.
