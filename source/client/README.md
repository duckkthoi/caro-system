# MODULE CLIENT

Caro Online có **hai client**. Cả hai nói cùng một giao thức JSON `{"type","reqId","data"}`
([`docs/protocol.md`](../docs/protocol.md)), nên người chơi desktop đấu được với người chơi web trong cùng một trận.

| Client | Thư mục | Công nghệ | Kết nối tới server |
|---|---|---|---|
| **Desktop** (bản Socket) | [`desktop/`](desktop/README.md) | Java 21, Swing + FlatLaf | **TCP thuần** `host:9090`, mỗi thông điệp một dòng |
| **Web** | [`web/`](web/README.md) | React 18 + TypeScript, Vite | **WebSocket** `/ws` + **REST** `/api` |

## 🎯 Hai client cùng làm những việc sau

- Gửi yêu cầu kèm `reqId`, chờ `OK`/`ERROR` có cùng `reqId` (quá 10 giây thì báo lỗi).
- Nhận **sự kiện server tự đẩy** (bàn cờ, phòng, người online, lời mời...) và cập nhật giao diện.
- Gửi `PING` mỗi 10 giây. `PONG` trả về giờ server, dùng để đếm ngược chính xác dù đồng hồ máy bị lệch.
- **Tự kết nối lại** bằng token khi mất mạng (thử mỗi 2 giây trong 30 giây, đúng bằng thời gian server giữ trận).
- **Không tự quyết định** nước đi hay kết quả: bàn cờ chỉ thay đổi khi server gửi `MOVE_APPLIED` / `MATCH_END`.

Hai client được tổ chức giống nhau:
- **tầng mạng:** `Connection` / `GameSocket`;
- **tầng trạng thái:** `GameClient` / `store`, cung cấp các hành động và phát sự kiện thay đổi;
- **tầng giao diện.**

Màn hình không làm việc trực tiếp với JSON hay socket.

## 🚀 Chạy nhanh

```bash
cd source && docker compose up --build        # server + web: http://localhost:5173
cd source/client/desktop && ./mvnw exec:java  # client desktop (cần JDK 21+)
```
