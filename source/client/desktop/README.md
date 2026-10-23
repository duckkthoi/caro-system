# CLIENT DESKTOP (Java Swing, TCP)

Client "bản Socket" của Caro Online. Kết nối tới server bằng **TCP thuần** (cổng `9090`). Mỗi thông điệp là
một dòng JSON, theo đúng [`docs/protocol.md`](../../docs/protocol.md). Client này không dùng HTTP: lịch sử,
bảng xếp hạng... cũng lấy qua socket (`GET_HISTORY`, `GET_LEADERBOARD`...).

## ⚙️ Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 |
| Giao diện | Swing + [FlatLaf](https://www.formdev.com/flatlaf/) (giao diện phẳng) |
| JSON | Jackson |
| Kết nối | `java.net.Socket`, mỗi thông điệp một dòng `\n` |
| Test | JUnit 5 |

## 🚀 Chạy

Yêu cầu: JDK 21+ và server đang chạy (`cd source && docker compose up`). Không cần cài Maven.

```bash
cd source/client/desktop
./mvnw exec:java                      # chạy thẳng      (Windows: mvnw.cmd exec:java)

./mvnw package                        # hoặc build ra một file jar duy nhất
java -jar target/caro-desktop.jar     # có thể chép file này sang máy khác để chạy
```

Địa chỉ server sửa được ngay trên màn hình đăng nhập. App nhớ server và username của lần đăng nhập trước.
Giá trị mặc định là `localhost:9090`, đổi được bằng tham số `--host=192.168.1.10 --port=9090`
hoặc bằng biến môi trường `CARO_HOST` / `CARO_PORT`.

**Chơi qua mạng LAN:** chạy server trên một máy, các máy khác nhập IP của máy đó vào ô Server
(nhớ mở cổng 9090 trên tường lửa của máy chạy server).

## 🎮 Chức năng

- Đăng ký, đăng nhập. Phiên chỉ có một: đăng nhập ở nơi khác thì phiên này bị đá ra.
- Sảnh:
  - danh sách phòng và người online, cập nhật trực tiếp;
  - tạo phòng, vào phòng;
  - mời người chơi;
  - **tìm trận nhanh** (ghép theo Elo, hiện khoảng điểm đang tìm và thời gian chờ).
- Phòng:
  - bàn cờ 15×15, đồng hồ 30 giây mỗi lượt (theo giờ server);
  - đánh dấu nước vừa đi, tô đường 5 quân thắng;
  - đầu hàng, rời phòng, **chơi lại** (đổi quân).
- **Tự kết nối lại** khi mất mạng: thử lại mỗi 2 giây trong 30 giây; trận đấu tiếp tục đúng trạng thái cũ.
- Hồ sơ và thống kê, lịch sử đấu, **xem lại từng nước** của trận đã đấu, bảng xếp hạng.

## 🧱 Cấu trúc

```
src/main/java/com/caro/client/
├── CaroDesktopApp.java    # main: đọc tham số, bật FlatLaf, mở cửa sổ
├── net/                   # tầng mạng, không biết gì về giao diện
│   ├── Connection.java    # socket, thread đọc, khớp reqId ↔ OK/ERROR, PING mỗi 10 giây
│   ├── Json.java          # ObjectMapper + phong bì {"type","reqId","data"}
│   └── ServerException.java
├── model/                 # record ứng với các kiểu dữ liệu trong protocol.md, mục 5
├── app/                   # trạng thái phía client, không phụ thuộc Swing
│   ├── GameClient.java    # hành động (login, move, invite...) + trạng thái + tự kết nối lại
│   ├── MatchView.java     # bản sao bàn cờ, chỉ thay đổi theo thông điệp từ server
│   └── ClientListener.java
└── ui/                    # Swing: MainFrame, LoginPanel, LobbyPanel, RoomPanel, BoardPanel, các dialog
```

**Luồng xử lý (thread):**
- `Connection` có một thread chỉ đọc socket. Mọi thông điệp nhận được (kể cả OK/ERROR) được chuyển sang
  **Swing thread** theo đúng thứ tự đến, nên `GameClient` và giao diện không cần khóa (lock).
- Chỉ việc mở kết nối (có thể chặn tới 5 giây) chạy ở thread nền.
- Client **không tự quyết định** kết quả: bấm vào ô chỉ gửi `MOVE`. Quân cờ chỉ hiện ra khi server gửi `MOVE_APPLIED`.

## 🧪 Test

```bash
./mvnw test                                          # ConnectionTest: dùng server giả, không cần Docker
CARO_LIVE_PORT=9090 ./mvnw test                      # thêm GameClientLiveTest: 2-4 client thật với server thật
```

| Test | Kiểm tra |
|---|---|
| `ConnectionTest` | Khớp `reqId` khi trả lời không theo thứ tự, lỗi `ERROR` thành `ServerException`, PING và độ lệch đồng hồ, server đóng kết nối thì request đang chờ thất bại và `onClosed` chỉ gọi một lần |
| `GameClientLiveTest` | Một ván đầy đủ rồi chơi lại (đổi quân), lịch sử và xem lại; mất mạng giữa trận rồi tự kết nối lại và đánh tiếp; ghép trận nhanh; mời và nhận lời mời |

Ảnh chụp màn hình cho README được tạo tự động bằng `ScreenshotTour` (trong `src/test`, xem hướng dẫn ở đầu file đó).
