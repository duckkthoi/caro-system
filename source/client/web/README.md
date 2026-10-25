# CLIENT WEB (React + TypeScript)

Client chạy trên trình duyệt của Caro Online:
- **WebSocket** `/ws` cho sảnh và trận đấu, cùng định dạng thông điệp với client TCP
  (xem [`docs/protocol.md`](../../docs/protocol.md));
- **REST** `/api` cho đăng nhập/đăng ký, hồ sơ, lịch sử, xem lại trận, bảng xếp hạng và trang quản trị.

## ⚙️ Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | TypeScript 5 |
| Giao diện | React 18, CSS thuần (không dùng thư viện UI) |
| Build / dev server | Vite 6 |
| Kết nối | `WebSocket` và `fetch` có sẵn của trình duyệt |
| Triển khai | nginx (Docker), phục vụ trang và chuyển `/api`, `/ws` tới server |
| Test | Node test runner (`node --test`, chạy TypeScript trực tiếp) |

## 🚀 Chạy

**Cùng toàn bộ hệ thống (Docker):**

```bash
cd source && docker compose up --build    # mở http://localhost:5173
```

**Khi phát triển** (cần Node 22+ và server đang chạy ở `localhost:8080`):

```bash
cd source/client/web
npm install
npm run dev          # http://localhost:5173
```

Vite chuyển `/api` và `/ws` sang server, nên trang và API cùng một origin và không cần cấu hình CORS.
Nếu server chạy ở địa chỉ khác, đặt biến môi trường `CARO_SERVER=http://host:8080` trước khi chạy `npm run dev`.

| Lệnh | Ý nghĩa |
|---|---|
| `npm run dev` | Dev server có hot reload |
| `npm run build` | Kiểm tra kiểu (`tsc`) rồi build ra `dist/` |
| `npm test` | Test logic bàn cờ (`src/**/*.test.ts`) |

**Thử 2 người chơi trên một máy:** mở 2 tab. Mỗi tab giữ token trong `sessionStorage` riêng,
nên mỗi tab đăng nhập được một tài khoản khác nhau. Tải lại trang thì vẫn giữ nguyên phiên.

## 🎮 Chức năng

- Đăng nhập, đăng ký. Bị đá ra nếu tài khoản đăng nhập ở nơi khác hoặc bị khóa.
- Sảnh:
  - danh sách phòng và người online (cập nhật trực tiếp);
  - tạo phòng (có thể bật **luật chặn hai đầu**), vào phòng, **xem** phòng bất kỳ, mời người chơi;
  - **tìm trận nhanh** theo Elo;
  - **chơi với máy** (Dễ/Khó, không tính Elo).
- Phòng:
  - bàn cờ 15×15, đồng hồ theo giờ server, tô nước vừa đi và đường thắng;
  - đầu hàng, rời phòng, **chơi lại (đổi quân)**;
  - **chat** với người chơi và người xem (React tự escape, tin nhắn luôn là văn bản thuần);
  - chế độ **người xem**: không đi được, chỉ có nút "Thôi xem".
- Lời mời: hiện ở góc màn hình, có đếm ngược, tự đóng khi server báo `INVITE_CLOSED`.
- **Tự kết nối lại** khi mất mạng: thử mỗi 2 giây trong 30 giây; trận tiếp tục.
- Hồ sơ, lịch sử đấu, **xem lại từng nước**, bảng xếp hạng.
- **Trang quản trị** (chỉ tài khoản `ADMIN`):
  - thống kê hệ thống;
  - tìm tài khoản, khóa và mở khóa;
  - xem phòng đang mở và các trận đã lưu.

## 🧱 Cấu trúc

```
src/
├── api/
│   ├── protocol.ts        # kiểu dữ liệu ứng với protocol.md, mục 5
│   ├── socket.ts          # GameSocket: reqId ↔ Promise, sự kiện push, PING 10 giây, lệch đồng hồ
│   └── rest.ts            # gọi REST kèm JWT, lỗi {"code","message"} thành ServerError
├── state/
│   ├── store.ts           # toàn bộ trạng thái + hành động, tự kết nối lại; React đọc qua useSyncExternalStore
│   ├── match.ts           # hàm thuần: áp MOVE_APPLIED, MATCH_END... vào bản sao bàn cờ
│   └── match.test.ts
├── components/            # Login, Lobby, Room, Board, Popups (lời mời + thông báo), Modals, hooks
├── admin/AdminPage.tsx    # trang quản trị
├── texts.ts               # nhãn tiếng Việt cho các mã từ server
└── App.tsx, main.tsx, styles.css
```

**Luồng dữ liệu:** component gọi hành động của `store` (ví dụ `store.move(x, y)`) → `GameSocket` gửi lên server →
sự kiện server đẩy về (`MOVE_APPLIED`...) → `store` tạo trạng thái mới (bất biến) → React vẽ lại.

Component không bao giờ tự đặt quân lên bàn cờ. Quân cờ chỉ xuất hiện khi server xác nhận bằng `MOVE_APPLIED`.
