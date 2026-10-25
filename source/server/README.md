# MODULE SERVER

Game server của hệ thống **Caro Online**. Đây là một ứng dụng Spring Boot duy nhất (monolith), cùng lúc phục vụ:

| Cổng | Giao thức | Dùng cho |
|---|---|---|
| `9090` | **TCP thuần**, mỗi dòng một JSON | Client desktop (bản Socket) |
| `8080` `/ws` | **WebSocket**, mỗi frame một JSON | Client Web |
| `8080` `/api/**` | **REST** (JSON, JWT) | Đăng ký/đăng nhập, hồ sơ, lịch sử đấu, bảng xếp hạng, quản trị (admin) |

Hai cổng game dùng **chung một lõi xử lý**, nên người chơi desktop đấu được với người chơi Web trong cùng một trận.

Tài liệu bắt buộc phải đọc trước khi sửa code:
- [`../docs/protocol.md`](../docs/protocol.md): định dạng thông điệp, mã lỗi, ví dụ.
- [`../docs/rules.md`](../docs/rules.md): luật chơi, thời gian, kết nối lại, Elo.

---

## ⚙️ CÔNG NGHỆ SỬ DỤNG

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 21 |
| Framework | Spring Boot 3.5 (Web, WebSocket, Security, Data JPA) |
| Database | PostgreSQL 17, migration bằng Flyway |
| Xác thực | JWT (jjwt, HS256), mật khẩu băm bằng BCrypt |
| Test | JUnit 5, AssertJ, H2 (chế độ PostgreSQL) |

---

## 🚀 HƯỚNG DẪN CHẠY

Yêu cầu: JDK 21 trở lên và Docker. **Không cần cài Maven**, vì đã có sẵn `mvnw`.

### Cách 1: Chạy toàn bộ bằng Docker
```bash
cd source
docker compose up --build
```

### Cách 2: Chạy server từ máy (để dev và debug)
```bash
cd source
docker compose up -d postgres        # chỉ bật database
cd server
./mvnw spring-boot:run               # Windows: mvnw.cmd spring-boot:run
```

> ⚠️ Nếu máy đã cài sẵn PostgreSQL và nó đang chiếm cổng 5432, server sẽ kết nối nhầm vào bản cài sẵn đó.
> Khi gặp trường hợp này, hãy tắt service PostgreSQL trên máy, hoặc đưa container ra cổng khác:
> `POSTGRES_PORT=5433 docker compose up -d postgres` rồi chạy server với `DB_URL=jdbc:postgresql://localhost:5433/caro`.
> Chạy cả hệ thống (`POSTGRES_PORT=5433 docker compose up --build`) cũng cần biến này để container Postgres mở được cổng.

### Kiểm tra nhanh
```bash
curl http://localhost:8080/api/health
# {"status":"ok","onlineUsers":0,"rooms":0}
```
Thử giao thức TCP bằng tay: `ncat localhost 9090`, sau đó gõ từng dòng JSON như ví dụ trong `docs/protocol.md`, mục 7.

Tài khoản admin được tạo tự động ở lần chạy đầu: `admin` / `admin123`.

### Cấu hình (biến môi trường)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/caro` | JDBC URL |
| `DB_USERNAME` / `DB_PASSWORD` | `caro` / `caro` | Tài khoản DB |
| `SERVER_PORT` | `8080` | Cổng REST và WebSocket |
| `TCP_PORT` | `9090` | Cổng TCP game |
| `JWT_SECRET` | (chuỗi dev) | Khóa ký JWT, tối thiểu 32 ký tự |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | Tài khoản admin tạo lần đầu |
| `CORS_ORIGINS` | `http://localhost:5173` | Origin của web client (Vite) |

Thời gian lượt, thời gian chờ kết nối lại, heartbeat... nằm trong `src/main/resources/application.yml`, mục `caro.*`.

---

## 🧱 KIẾN TRÚC

```
TCP :9090 ─► TcpServer ──────────┐
                                 ├─► MessageDispatcher ─► AuthService
WS  /ws   ─► GameWebSocketHandler┘        │               LobbyService ─► GameMatch ─► RuleEngine / Elo
                                          ▼                    │               │
                                   SessionRegistry ◄───────────┴── (push sự kiện)
                                                                               │ kết thúc trận
REST /api ─► AuthController ─► AuthService                         MatchPersistenceService ─► PostgreSQL
          ─► PlayerController ─► PlayerQueryService ◄── (GET_PROFILE, GET_HISTORY... từ MessageDispatcher)
          ─► AdminController  ─► AdminService ─► SessionRegistry.kick() khi khóa tài khoản
```

| Package | Nội dung |
|---|---|
| `net` | Lõi giao thức, không phụ thuộc tầng truyền tải: `ClientSession`, `SessionRegistry`, `MessageDispatcher`, `HeartbeatMonitor`, `RateLimiter` |
| `net.tcp` | `TcpServer`: `ServerSocket`, một virtual thread cho mỗi kết nối, framing bằng `\n` |
| `net.ws` | `GameWebSocketHandler`: WebSocket thuần (không dùng STOMP) |
| `lobby` | Mọi thứ ngoài trận đấu: phòng, người xem, chat, danh sách online, ghép trận nhanh (`MatchmakingQueue`), lời mời, chơi lại, mở phòng chơi với máy; xử lý mất kết nối và kết nối lại ở mức phòng |
| `match` | `GameMatch` (trạng thái trận trong RAM, đồng hồ lượt, chờ kết nối lại), lưu kết quả xuống DB |
| `game` | Java thuần: `Board`, `RuleEngine` (kiểm tra 5 quân, tùy chọn luật chặn hai đầu), `Elo` |
| `bot` | Đối thủ máy: `BotBrain` (hàm thuần chọn nước đi theo điểm tấn công/phòng thủ), `BotPlayer` (nối bot vào một trận) |
| `player` | Đọc dữ liệu đã lưu: hồ sơ, thống kê, lịch sử, xem lại trận, bảng xếp hạng. Dùng chung cho REST và socket |
| `admin` | `/api/admin/**`: thống kê hệ thống, tìm/khóa/mở khóa tài khoản, xem phòng và trận |
| `auth`, `user` | Đăng ký, đăng nhập, JWT, entity `User` |
| `config`, `common` | Cấu hình Spring, mã lỗi, xử lý lỗi REST |

### Các quyết định thiết kế (hay bị hỏi khi vấn đáp)

1. **Server là nguồn sự thật.** Client chỉ gửi ý định (`MOVE x,y`). Việc kiểm tra lượt, ô trống, thắng/thua
   và Elo đều chạy trong `GameMatch`.
2. **Mỗi kết nối một virtual thread** (Java 21). Code viết theo kiểu blocking I/O nên dễ đọc, nhưng không
   tốn 1 MB stack cho mỗi thread như platform thread.
3. **Gửi bất đồng bộ.** `ClientSession.send()` chỉ đẩy thông điệp vào hàng đợi; một writer thread riêng của từng
   phiên mới thực sự ghi ra socket. Nhờ vậy game logic có thể gửi trong khi đang giữ khóa mà không bị một
   client chậm làm treo. Client không đọc kịp (hàng đợi đầy) sẽ bị ngắt.
4. **Khóa theo từng trận.** Mọi thay đổi của một trận nằm trong `synchronized (match)`, nên nước đi, hết giờ,
   đầu hàng hay mất kết nối luôn được áp dụng lần lượt. Trận chỉ kết thúc **đúng một lần** (`finish()`).
   Các trận khác nhau chạy song song với nhau.
5. **Thứ tự khóa cố định: lobby, rồi mới tới match.** Match chỉ gọi lại lobby (`onMatchEnded`) **sau khi** đã
   nhả khóa của mình, nên không thể xảy ra deadlock.
6. **Heartbeat.** TCP có thể mất rất lâu mới phát hiện được peer đã biến mất. Vì vậy client phải gửi `PING`
   mỗi 10 giây, và phiên nào im lặng quá 30 giây sẽ bị đóng.
7. **Mỗi tài khoản một phiên.** Đăng nhập lại sẽ đá phiên cũ. Đây cũng chính là cơ chế kết nối lại.
8. **REST và socket dùng chung một service đọc dữ liệu.** `PlayerQueryService` trả về cùng một JSON cho
   `GET /api/users/{id}/matches` và `GET_HISTORY`, nên client desktop không cần biết HTTP.
9. **Khóa tài khoản an toàn khi đang có trận.** Admin khóa thì trạng thái được ghi DB trước (chỉ cập nhật cột
   `status`, không ghi đè `rating`), sau đó mới đá phiên đang online. Trận đang chơi xử lý như mất kết nối.
   JWT cũ cũng mất hiệu lực ngay, vì mỗi request đều đọc lại user từ DB.
10. **Một khóa cho toàn bộ sảnh.** Phòng, hàng chờ, lời mời và danh sách online cùng nằm dưới khóa của
    `LobbyService`. Nhờ vậy một người không thể vừa được ghép ngẫu nhiên vừa nhận lời mời rồi rơi vào hai trận.
    Thao tác ở sảnh hiếm hơn nhiều so với nước đi, nên một khóa là đủ; còn nước đi chỉ khóa theo từng trận.
11. **Thuật toán ghép tách riêng.** `MatchmakingQueue` là Java thuần (không khóa, không mạng, thời gian truyền vào
    dưới dạng tham số), nên test được hết các trường hợp nới khoảng điểm mà không phải chờ thật.
12. **Người xem nhận đúng các sự kiện của người chơi.** `GameMatch` giữ danh sách người xem trong cùng khóa với
    trận. Người xem mới được thêm vào **cùng lúc** với việc gửi `MATCH_STATE` cho họ (trong khóa), nên không bao giờ
    thiếu hay nhận trùng một nước đi. Người xem không có quân (`yourMark = null`), nên mọi thao tác đi/đầu hàng đều bị
    từ chối bởi đúng đoạn kiểm tra đã có sẵn.
13. **Bot là một "client" bên trong server.** `BotPlayer` nhận sự kiện qua `Notifier` của trận (sự kiện gửi cho id
    `-1`), đọc cùng ảnh chụp trạng thái mà client nhận, rồi đi bằng `GameMatch.move()`. Vì vậy nước đi của máy bị kiểm
    tra bởi đúng luật của người thật. Sự kiện tới trong lúc trận đang giữ khóa, nên bot chỉ **lên lịch** suy nghĩ trên
    scheduler (không bao giờ đi ngay trong khóa), và khi chạy thì đọc lại trạng thái, chỉ đi nếu vẫn tới lượt.
    Trận với máy có `rated = false`: Elo bằng 0 và không lưu DB.
14. **Rate limit bằng token bucket theo kết nối.** Mỗi phiên có một xô chung (mọi thông điệp trừ `PING`, kể cả
    JSON hỏng) và một xô riêng cho `CHAT`. Bị chặn thì trả `RATE_LIMITED` chứ không đóng kết nối. `PING` không bị
    tính để heartbeat không bao giờ làm người chơi bị coi là mất kết nối.
15. **Luật chặn hai đầu lưu cùng trận** (cột `block_two_ends`, migration `V2`), vì đường thắng khi xem lại được
    tính lại từ các nước đi và phải dùng đúng luật của trận đó.

---

## 🧪 TEST

```bash
./mvnw test
```
Không cần Docker, vì test dùng H2 in-memory.

| Test | Kiểm tra |
|---|---|
| `RuleEngineTest` | Thắng theo 4 hướng, ở mép bàn, 6 quân, bị chặn hai đầu, bị quân đối phương cắt; luật chặn hai đầu (bị chặn hai đầu không thắng, một đầu vẫn thắng, mép bàn không tính, hướng khác vẫn thắng, 6 quân) |
| `BotBrainTest` | Bot đi giữa bàn khi bàn trống, tự thắng khi có 4, chặn 4 và 3 mở của đối thủ, ưu tiên thắng hơn chặn, hiểu luật chặn hai đầu, luôn đi vào ô trống |
| `RateLimiterTest` | Token bucket: cho phép dồn tối đa, nạp lại đúng tốc độ, không tích quá mức tối đa |
| `EloTest` | Công thức Elo |
| `MatchmakingQueueTest` | Ghép ngay khi điểm gần nhau, chênh nhiều thì phải chờ cả hai nới khoảng, ghép bất kỳ sau 30 giây, ưu tiên người chờ lâu nhất và đối thủ gần điểm nhất |
| `GameMatchTest` | Lượt đi, ô không hợp lệ, thắng/hòa/đầu hàng/hết giờ, kết thúc đúng một lần, timer cũ bị bỏ qua, mất kết nối/kết nối lại/hủy trận; người xem nhận sự kiện nhưng không đi được; trận không tính Elo; luật chặn hai đầu |
| `GameFlowIntegrationTest` | Chạy thật qua socket: **client TCP đấu với client WebSocket**, lưu DB và Elo, kết nối lại giữa trận, đăng nhập lần hai đá phiên cũ, lỗi giao thức |
| `LobbyFeaturesIntegrationTest` | Ghép trận nhanh (X/O ngẫu nhiên, trạng thái online đổi theo), hủy tìm trận, lời mời được đồng ý/từ chối/hủy/hết hạn/đóng khi người kia offline, đồng ý lời mời thì tự rời hàng chờ, chơi lại đổi quân |
| `RoomExtrasIntegrationTest` | Qua TCP thật: người xem thấy trận và nhận nước đi, chat (cả người chơi và người xem, ký tự điều khiển bị thay, giới hạn độ dài), người xem bị đưa ra khi phòng đóng, người xem vào ngồi chơi được; phòng chặn hai đầu chơi tiếp sau 5 quân bị chặn và luật được lưu; chơi với máy (không tính Elo, không lưu, chơi lại đổi quân, máy đi trước), máy mức khó thắng người chơi đi lung tung |
| `RateLimitIntegrationTest` | Gửi quá nhanh bị `RATE_LIMITED` (cả JSON hỏng cũng bị tính), `PING` không bị chặn, chat có giới hạn riêng |
| `RestApiIntegrationTest` | Sau một trận: hồ sơ, lịch sử, xem lại trận, bảng xếp hạng (REST và socket ra cùng kết quả); mã lỗi REST 400/401/403/404; admin khóa người đang trong trận (bị `KICKED`, đối thủ nhận `OPPONENT_DISCONNECTED`, token cũ hết hiệu lực) rồi mở khóa |

---

## ➕ MỞ RỘNG

**Thêm một loại thông điệp mới** (ví dụ `CHAT`):
1. Đặc tả trong `docs/protocol.md`.
2. Thêm giá trị vào `ClientMessageType` (và `ServerMessageType` nếu có sự kiện mới).
3. Thêm một nhánh `case` trong `MessageDispatcher.handle()` và gọi sang service tương ứng.
4. Viết test integration trong `src/test/java/com/caro/server/net/` (ví dụ `RoomExtrasIntegrationTest`).

**Thêm REST API**: tạo controller dưới `/api/...`. Các route `/api/admin/**`
đã được chặn, chỉ cho phép role `ADMIN`. Lấy người đang đăng nhập bằng `@AuthenticationPrincipal AuthenticatedUser me`.
Dữ liệu trận nằm ở bảng `matches` và `match_moves` (entity `MatchRecord`).

**Đổi schema DB:** tạo file migration mới `V2__....sql`. **Không sửa** `V1__init.sql`, vì Flyway sẽ báo lỗi checksum
trên những máy đã chạy migration cũ.
