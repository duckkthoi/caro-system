# ĐẶC TẢ USE CASE: CARO ONLINE

> Mô tả chi tiết 37 use case của hệ thống, viết theo hành vi **hiện tại** của code (server, client desktop, client web).
> Luật chơi gốc nằm ở [rules.md](rules.md), định dạng thông điệp ở [protocol.md](protocol.md). Hai file đó là hợp đồng.
> File này giải thích use case nào chạy qua thông điệp nào, hàm nào và test nào, để mỗi thành viên ôn phần của mình.

Mỗi use case gồm: actor, mô tả, tiền điều kiện, hậu điều kiện, luồng chính, luồng ngoại lệ (mã lỗi trả về),
mã nguồn liên quan, test đã có và vài điểm hay bị hỏi khi vấn đáp.

---

## 0. Tổng quan

### 0.1. Actor

| Actor | Là ai |
|---|---|
| **Khách** | Người chưa đăng nhập. Chỉ được gửi `PING`, `REGISTER`, `LOGIN`, `AUTH` |
| **Người chơi** (Player) | Tài khoản role `PLAYER` đã đăng nhập, qua desktop (TCP) hoặc web (WebSocket + REST) |
| **Người xem** (Spectator) | Một người chơi đang xem phòng của người khác. Không phải tài khoản riêng |
| **Quản trị viên** (Admin) | Tài khoản role `ADMIN`. Dùng trang Quản trị trên web |
| **Hệ thống** | Các cơ chế chạy nền trên server: bộ đếm giờ, heartbeat, hàng chờ ghép trận, rate limit, ghi DB |
| **Bot** | Người chơi máy chạy trong server, id `-1`. Đi qua đúng luật như người thật |

### 0.2. Danh sách 37 use case

| Mã | Use case | Actor chính | Nhóm |
|---|---|---|---|
| UC01 | Đăng ký tài khoản | Khách | 1. Tài khoản và xác thực |
| UC02 | Đăng nhập | Khách | 1 |
| UC03 | Đăng xuất | Người chơi | 1 |
| UC04 | Kiểm soát đơn phiên | Hệ thống | 1 |
| UC05 | Xem danh sách người chơi online | Người chơi | 2. Sảnh và tìm đối thủ |
| UC06 | Xem danh sách phòng | Người chơi | 2 |
| UC07 | Tạo phòng | Người chơi | 2 |
| UC08 | Vào phòng chờ | Người chơi | 2 |
| UC09 | Thách đấu / mời người chơi | Người chơi | 2 |
| UC10 | Trả lời thách đấu | Người chơi | 2 |
| UC11 | Hủy lời mời | Người chơi | 2 |
| UC12 | Tìm trận nhanh | Người chơi, Hệ thống | 2 |
| UC13 | Đánh với máy (Bot) | Người chơi, Bot | 2 |
| UC14 | Thực hiện nước đi | Người chơi | 3. Trận đấu và trọng tài |
| UC15 | Đếm giờ lượt đi | Hệ thống | 3 |
| UC16 | Cập nhật bàn cờ | Hệ thống | 3 |
| UC17 | Phán quyết thắng / hòa | Hệ thống | 3 |
| UC18 | Đầu hàng | Người chơi | 3 |
| UC19 | Cầu hòa | Người chơi | 3 |
| UC20 | Thoát / rời phòng giữa ván | Người chơi | 3 |
| UC21 | Xử lý mất kết nối và kết nối lại | Người chơi, Hệ thống | 3 |
| UC22 | Chơi lại (Rematch) | Người chơi | 3 |
| UC23 | Xem trận đấu | Người xem | 4. Phòng và người xem |
| UC24 | Thôi xem trận | Người xem | 4 |
| UC25 | Trò chuyện trong phòng | Người chơi, Người xem | 4 |
| UC26 | Xem hồ sơ cá nhân / đối thủ | Người chơi | 5. Hồ sơ, lịch sử, xếp hạng |
| UC27 | Xem lịch sử đấu | Người chơi | 5 |
| UC28 | Phát lại ván đấu | Người chơi | 5 |
| UC29 | Xem bảng xếp hạng | Người chơi | 5 |
| UC30 | Xem thống kê hệ thống | Admin | 6. Quản trị |
| UC31 | Quản lý danh sách người dùng | Admin | 6 |
| UC32 | Khóa tài khoản | Admin | 6 |
| UC33 | Mở khóa tài khoản | Admin | 6 |
| UC34 | Giám sát phòng và trận đấu | Admin | 6 |
| UC35 | Kiểm tra nhịp tim (heartbeat) | Hệ thống | 7. Dịch vụ nền |
| UC36 | Giới hạn tốc độ (rate limit) | Hệ thống | 7 |
| UC37 | Tính Elo và lưu ván cờ vào CSDL | Hệ thống | 7 |

6 use case UC04, UC15, UC16, UC35, UC36, UC37 là **cơ chế hệ thống**, người dùng không trực tiếp bấm. Khi vẽ sơ đồ use case
nên nối chúng vào use case chính bằng `<<include>>` (ví dụ UC14 include UC15, UC16, UC17) hoặc đưa vào phần kiến trúc.

### 0.3. Phân công gợi ý (4 người)

Mỗi người nắm **một lát cắt dọc**: giao diện desktop và web, thông điệp, xử lý server, DB.

| Người | Use case | File server chính |
|---|---|---|
| 1. Mạng và tài khoản | UC01–04, UC35, UC36 | `net/*`, `auth/*`, `SecurityConfig` |
| 2. Sảnh và ghép trận | UC05–13 | `LobbyService`, `MatchmakingQueue`, `Room`, `bot/*` |
| 3. Trận đấu và trọng tài | UC14–22 | `GameMatch`, `RuleEngine`, `Board` |
| 4. Dữ liệu, người xem, quản trị | UC23–34, UC37 | `player/*`, `admin/*`, `MatchPersistenceService`, `Elo`, migration |

### 0.4. Luồng chung của mọi yêu cầu qua socket

Phần này giống nhau cho mọi use case đi qua TCP/WebSocket, nên chỉ viết một lần ở đây.

1. Client gửi một phong bì JSON `{"type","reqId","data"}`:
   - desktop: một dòng kết thúc bằng `\n` qua TCP cổng 9090;
   - web: một text frame qua WebSocket `/ws`.
2. Tầng truyền tải đọc thông điệp rồi gọi `MessageDispatcher.onMessage()`:
   - TCP: `TcpServer.serve()` chạy một virtual thread cho mỗi kết nối, `readLine()` giới hạn 8192 ký tự;
   - WebSocket: `GameWebSocketHandler`.
3. `onMessage()` xử lý theo thứ tự:
   1. `session.touch()` cập nhật lần cuối nghe thấy client, dùng cho heartbeat (UC35);
   2. parse JSON;
   3. rate limit chung (UC36);
   4. kiểm tra `type` có tồn tại không;
   5. kiểm tra đã đăng nhập chưa (`ClientMessageType.requiresAuth()`);
   6. gọi `handle()`.
4. `handle()` là một `switch` theo `type`, gọi service tương ứng: `AuthService`, `LobbyService`, `PlayerQueryService`.
5. Thành công thì server trả `OK` cùng `reqId`. Service ném `AppException(code, message)` thì trả `ERROR` cùng `reqId`.
   Lỗi bất ngờ trả `INTERNAL_ERROR`.
6. Sự kiện đẩy cho người khác đi qua `SessionRegistry.sendToUser()` / `broadcast()`, rồi tới `ClientSession.send()`.
   `send()` chỉ đẩy vào hàng đợi, **không bao giờ block**. Mỗi phiên có một writer thread riêng ghi ra socket.
   Hàng đợi đầy (1000 thông điệp) nghĩa là client đọc quá chậm, và kết nối bị ngắt.
7. Phía client:
   - desktop: `Connection` có một thread đọc, ghép `reqId` với `CompletableFuture`, đẩy sự kiện về EDT;
     `GameClient.handleEvent()` cập nhật trạng thái rồi báo cho các màn hình trong `ui/`;
   - web: `GameSocket` (`api/socket.ts`) ghép `reqId` với `Promise`;
     `store.ts` xử lý sự kiện trong `handleEvent` (khối `switch (type)`), React vẽ lại qua `useSyncExternalStore`.

Lưu ý: sự kiện push có thể tới **trước** `OK` của chính yêu cầu đã gây ra nó (ví dụ `MOVE_APPLIED` tới trước `OK` của `MOVE`).

**Khóa:**
- Mọi trạng thái sảnh (phòng, hàng chờ, lời mời, danh sách online, người xem) nằm trong `LobbyService` và dùng chung một khóa `lock`.
- Mỗi `GameMatch` có khóa riêng (`synchronized (this)`).
- Thứ tự khóa luôn là **lobby rồi mới đến match**. `GameMatch` chỉ gọi `MatchEndListener.onMatchEnded()` sau khi đã nhả khóa của nó.

### 0.5. Bản đồ file

| Phần | File |
|---|---|
| Điều phối thông điệp | [MessageDispatcher.java](../server/src/main/java/com/caro/server/net/MessageDispatcher.java), [ClientMessageType.java](../server/src/main/java/com/caro/server/net/ClientMessageType.java) |
| Phiên, truyền tải | [ClientSession.java](../server/src/main/java/com/caro/server/net/ClientSession.java), [SessionRegistry.java](../server/src/main/java/com/caro/server/net/SessionRegistry.java), [TcpServer.java](../server/src/main/java/com/caro/server/net/tcp/TcpServer.java), [GameWebSocketHandler.java](../server/src/main/java/com/caro/server/net/ws/GameWebSocketHandler.java) |
| Heartbeat, rate limit | [HeartbeatMonitor.java](../server/src/main/java/com/caro/server/net/HeartbeatMonitor.java), [RateLimiter.java](../server/src/main/java/com/caro/server/net/RateLimiter.java), [LoginThrottle.java](../server/src/main/java/com/caro/server/auth/LoginThrottle.java), [ClientIp.java](../server/src/main/java/com/caro/server/auth/ClientIp.java) |
| Tài khoản | [AuthService.java](../server/src/main/java/com/caro/server/auth/AuthService.java), [JwtService.java](../server/src/main/java/com/caro/server/auth/JwtService.java), [JwtAuthFilter.java](../server/src/main/java/com/caro/server/auth/JwtAuthFilter.java), [AuthController.java](../server/src/main/java/com/caro/server/auth/AuthController.java), [SecurityConfig.java](../server/src/main/java/com/caro/server/config/SecurityConfig.java) |
| Sảnh | [LobbyService.java](../server/src/main/java/com/caro/server/lobby/LobbyService.java), [Room.java](../server/src/main/java/com/caro/server/lobby/Room.java), [MatchmakingQueue.java](../server/src/main/java/com/caro/server/lobby/MatchmakingQueue.java), [Invitation.java](../server/src/main/java/com/caro/server/lobby/Invitation.java) |
| Bot | [BotPlayer.java](../server/src/main/java/com/caro/server/bot/BotPlayer.java), [BotBrain.java](../server/src/main/java/com/caro/server/bot/BotBrain.java) |
| Trận đấu | [GameMatch.java](../server/src/main/java/com/caro/server/match/GameMatch.java), [RuleEngine.java](../server/src/main/java/com/caro/server/game/RuleEngine.java), [Board.java](../server/src/main/java/com/caro/server/game/Board.java), [Elo.java](../server/src/main/java/com/caro/server/game/Elo.java) |
| Lưu trận | [MatchPersistenceService.java](../server/src/main/java/com/caro/server/match/MatchPersistenceService.java), [MatchRecord.java](../server/src/main/java/com/caro/server/match/MatchRecord.java), [UserRepository.java](../server/src/main/java/com/caro/server/user/UserRepository.java), [migration](../server/src/main/resources/db/migration) |
| Truy vấn | [PlayerQueryService.java](../server/src/main/java/com/caro/server/player/PlayerQueryService.java), [PlayerController.java](../server/src/main/java/com/caro/server/player/PlayerController.java) |
| Quản trị | [AdminService.java](../server/src/main/java/com/caro/server/admin/AdminService.java), [AdminController.java](../server/src/main/java/com/caro/server/admin/AdminController.java) |
| Desktop | [GameClient.java](../client/desktop/src/main/java/com/caro/client/app/GameClient.java), [Connection.java](../client/desktop/src/main/java/com/caro/client/net/Connection.java), [ui/](../client/desktop/src/main/java/com/caro/client/ui) |
| Web | [store.ts](../client/web/src/state/store.ts), [socket.ts](../client/web/src/api/socket.ts), [rest.ts](../client/web/src/api/rest.ts), [components/](../client/web/src/components), [AdminPage.tsx](../client/web/src/admin/AdminPage.tsx) |
| Test server | [GameMatchTest.java](../server/src/test/java/com/caro/server/match/GameMatchTest.java), [net/*IntegrationTest](../server/src/test/java/com/caro/server/net) |

---

## 1. Tài khoản và xác thực

### UC01. Đăng ký tài khoản

| | |
|---|---|
| **Actor** | Khách |
| **Mô tả** | Tạo tài khoản mới với username, mật khẩu và tên hiển thị |
| **Tiền điều kiện** | Chưa đăng nhập. IP chưa vượt giới hạn đăng nhập/đăng ký (UC36) |
| **Hậu điều kiện** | Có một dòng mới trong bảng `users`: role `PLAYER`, status `ACTIVE`, rating 1000, mật khẩu đã băm BCrypt |
| **Thông điệp / API** | Socket `REGISTER` · REST `POST /api/auth/register` (trả `201`) |

**Luồng chính**
1. Người dùng nhập username, mật khẩu, tên hiển thị (có thể bỏ trống) rồi bấm Đăng ký.
2. Client gửi yêu cầu:
   - desktop mở kết nối TCP rồi gửi `REGISTER`;
   - web gọi `POST /api/auth/register`.
3. Server đếm một lần thử theo IP (`LoginThrottle.check()`):
   - socket: gọi thẳng trong `handle()`;
   - REST: qua `LoginThrottleFilter`.
4. `AuthService.register()` chuẩn hóa username (trim, đổi chữ thường) rồi kiểm tra:
   - username khớp `[a-z0-9_]{3,20}`;
   - mật khẩu từ 6 ký tự trở lên và tối đa 72 byte UTF-8;
   - tên hiển thị: thay ký tự điều khiển bằng dấu cách, bỏ trống thì dùng username, tối đa 30 ký tự;
   - username chưa tồn tại.
5. Băm mật khẩu bằng BCrypt (`passwordEncoder.encode`), lưu `User` bằng `saveAndFlush`.
6. Server trả `UserInfo`. Cả hai client sau đó **tự đăng nhập luôn** bằng tài khoản vừa tạo (UC02).

**Luồng ngoại lệ**
- Dữ liệu sai: `VALIDATION_ERROR`.
- Username đã có: `USERNAME_TAKEN`. Trường hợp hai người đăng ký cùng username cùng lúc, cả hai đều qua bước kiểm tra
  `existsByUsername`, nhưng ràng buộc `UNIQUE` của DB chặn người thứ hai. Lỗi `DataIntegrityViolationException`
  được đổi thành `USERNAME_TAKEN`.
- Quá nhiều lần từ một IP: `RATE_LIMITED` (REST trả HTTP 429).

**Mã nguồn**
- Server: `MessageDispatcher.handle()` case `REGISTER`, `AuthController`, `AuthService.register()`, `LoginThrottle`
- Desktop: `GameClient.register()`, `ui/LoginPanel`
- Web: `store.register()`, `rest.register`, `components/Login.tsx`
- Test: `RestApiIntegrationTest.passwordsAreLimitedInBytesAndDisplayNamesLoseControlCharacters`

**Hay bị hỏi**
- *Vì sao giới hạn 72 byte?* BCrypt chỉ dùng 72 byte đầu của mật khẩu. Không chặn thì hai mật khẩu chung 72 byte đầu sẽ được coi là một.
  Trước đây gửi mật khẩu dài hơn còn gây lỗi 500.
- *Vì sao băm BCrypt?* BCrypt có salt ngẫu nhiên và cố tình chạy chậm, nên DB có bị lộ thì cũng khó dò ngược ra mật khẩu.

---

### UC02. Đăng nhập

| | |
|---|---|
| **Actor** | Khách |
| **Mô tả** | Xác thực bằng username/mật khẩu, nhận JWT và gắn phiên với tài khoản |
| **Tiền điều kiện** | Có tài khoản, không bị khóa. Phiên hiện tại chưa đăng nhập |
| **Hậu điều kiện** | Phiên được gắn với user. User xuất hiện trong danh sách online. Phiên cũ của cùng tài khoản (nếu có) bị đá (UC04) |
| **Thông điệp / API** | Desktop: `LOGIN`. Web: `POST /api/auth/login`, sau đó gửi `AUTH {token}` qua WebSocket. `GET /api/auth/me` |

**Luồng chính (desktop)**
1. Nhập địa chỉ server, username, mật khẩu. `GameClient.login()` mở TCP (`Connection.open`) rồi gửi `LOGIN`.
2. Server:
   1. `requireAnonymous()` kiểm tra phiên chưa đăng nhập;
   2. `LoginThrottle.check(ip)` (UC36);
   3. `AuthService.login()`: tìm user theo username đã chuẩn hóa, so mật khẩu bằng `passwordEncoder.matches`, kiểm tra không bị khóa;
   4. `JwtService.issue()` tạo JWT HS256, hạn 24 giờ, `subject` là user id.
3. `completeLogin()`:
   1. `SessionRegistry.bindUser()` gắn phiên với user; nếu đã có phiên cũ thì đá phiên cũ (UC04);
   2. trả `OK {token, user}`;
   3. gọi `LobbyService.onUserConnected()`: thêm vào danh sách online; nếu đang ở trong phòng/trận (kết nối lại)
      thì gửi lại `ROOM_UPDATE` và `MATCH_STATE` (UC21); cuối cùng broadcast `ROOM_LIST` và `ONLINE_USERS`.
4. Client giữ token để dùng khi tự kết nối lại. `synchronizeState()` gửi `LIST_ROOMS`, `LIST_ONLINE`, `GET_STATE` để vẽ sảnh.

**Luồng chính (web)**
1. `store.login()` gọi `POST /api/auth/login` và nhận `{token, user}`.
2. Mở WebSocket `/ws`, gửi `AUTH {token}` làm thông điệp đầu tiên.
3. Server `AuthService.authenticate()`: kiểm tra chữ ký và hạn của JWT, **đọc lại user trong DB** (tài khoản vừa bị khóa
   thì bị từ chối dù token còn hạn), rồi `completeLogin()` như desktop.
4. Token được lưu trong `sessionStorage` (riêng từng tab). Tải lại trang thì `store.restore()` dùng lại token.

**Luồng ngoại lệ**
- Sai username hoặc mật khẩu: `INVALID_CREDENTIALS`. Server không nói rõ sai cái nào, để không lộ username nào tồn tại.
- Tài khoản bị khóa: `ACCOUNT_LOCKED`.
- Token sai hoặc hết hạn (`AUTH`): `UNAUTHORIZED`.
- Gửi `LOGIN`/`AUTH` khi phiên đã đăng nhập: `ALREADY_AUTHENTICATED`.
- Quá nhiều lần thử: `RATE_LIMITED`.

**Mã nguồn**
- Server: `MessageDispatcher` case `LOGIN`/`AUTH`, `completeLogin()`, `AuthService.login()`/`authenticate()`, `JwtService`,
  `JwtAuthFilter` (REST), `SessionRegistry.bindUser()`, `LobbyService.onUserConnected()`
- Desktop: `GameClient.login()`, `loggedIn()`, `synchronizeState()`, `ui/LoginPanel`
- Web: `store.login()`, `store.restore()`, `api/socket.ts`, `components/Login.tsx`
- Test: `GameFlowIntegrationTest.tcpPlayerBeatsWebPlayerAndTheResultIsStored` (đăng nhập cả TCP lẫn WS)

**Hay bị hỏi**
- *Vì sao web phải lấy token qua REST rồi mới gửi `AUTH`, còn desktop gửi thẳng `LOGIN`?* Web cần token để gọi các API REST
  (lịch sử, admin...). Desktop làm mọi thứ qua socket nên `LOGIN` trả luôn token, chỉ dùng khi tự kết nối lại.
- *REST không có session, server biết ai đang gọi bằng cách nào?* Mỗi request gửi header `Authorization: Bearer <JWT>`.
  `JwtAuthFilter` kiểm tra token và đặt user vào `SecurityContext`. Server cấu hình `STATELESS`, không lưu session HTTP.

---

### UC03. Đăng xuất

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Chủ động thoát khỏi hệ thống |
| **Tiền điều kiện** | Đã đăng nhập |
| **Hậu điều kiện** | Rời phòng hoặc thôi xem (nếu có). Nếu đang trong trận thì bị tính thua (lý do `RESIGN`). Kết nối bị đóng, biến mất khỏi danh sách online |
| **Thông điệp** | `LOGOUT` |

**Luồng chính**
1. Bấm Đăng xuất. Client đặt cờ `loggingOut` (để không tự kết nối lại) rồi gửi `LOGOUT`.
2. Server `LobbyService.leaveRoomIfPresent()`:
   - đang là người chơi trong phòng: `removeFromRoom()`, trận đang chạy thì `GameMatch.forfeitIfRunning()` (thua, lý do `RESIGN`);
   - đang xem: `stopSpectating()`.
3. Server trả `OK`, rồi `session.closeAfterFlush()`: gửi nốt các thông điệp còn trong hàng đợi rồi mới đóng kết nối.
4. Kết nối đóng nên `MessageDispatcher.onClose()` chạy, rồi `LobbyService.onUserDisconnected()`:
   bỏ khỏi online, hàng chờ, hủy lời mời liên quan, broadcast danh sách mới.
5. Client quay về màn hình đăng nhập. Web xóa token trong `sessionStorage`.

**Luồng ngoại lệ:** mất kết nối giữa chừng thì client vẫn coi như đã đăng xuất (không tự kết nối lại vì cờ `loggingOut`).

**Mã nguồn**
- Server: `MessageDispatcher` case `LOGOUT`, `LobbyService.leaveRoomIfPresent()`, `removeFromRoom()`, `ClientSession.closeAfterFlush()`
- Desktop: `GameClient.logout()`. Web: `store.logout()`

**Hay bị hỏi:** *Vì sao dùng `closeAfterFlush()` chứ không `close()` luôn?* Để client chắc chắn nhận được `OK` và `MATCH_END`
trước khi kết nối bị đóng. `close()` sẽ bỏ các thông điệp còn trong hàng đợi.

---

### UC04. Kiểm soát đơn phiên

| | |
|---|---|
| **Actor** | Hệ thống |
| **Mô tả** | Mỗi tài khoản chỉ có một phiên hoạt động. Đăng nhập ở nơi khác (TCP hay web đều được) thì phiên cũ bị đá |
| **Tiền điều kiện** | Tài khoản đang có một phiên online và đăng nhập thêm một lần nữa |
| **Hậu điều kiện** | Phiên mới là phiên duy nhất. Phiên cũ nhận `KICKED {reason: LOGGED_IN_ELSEWHERE}` rồi bị đóng |
| **Thông điệp** | Push `KICKED` |

**Luồng chính**
1. Phiên mới đăng nhập thành công (UC02). `SessionRegistry.bindUser()` ghi phiên mới vào map `byUser` và trả về phiên cũ.
2. `completeLogin()` thấy phiên cũ khác phiên mới thì gọi `registry.kick(previous, "LOGGED_IN_ELSEWHERE")`:
   gửi `KICKED` rồi `closeAfterFlush()`.
3. Phiên cũ đóng nên `onClose()` chạy. `registry.remove()` trả `false` vì `byUser` đã trỏ sang phiên mới,
   nên **không** gọi `onUserDisconnected()`. Người chơi không bị coi là offline và trận vẫn chạy.
4. `LobbyService.onUserConnected()` gắn phiên mới vào phòng/trận đang có và gửi lại `ROOM_UPDATE`, `MATCH_STATE`.
5. Client cũ nhận `KICKED`: báo "Tài khoản đã đăng nhập ở nơi khác", về màn hình đăng nhập và **không** tự kết nối lại.

**Mã nguồn**
- Server: `SessionRegistry.bindUser()`, `kick()`, `remove()`, `MessageDispatcher.completeLogin()`
- Desktop: `GameClient.handleEvent()` case `KICKED`. Web: `store.ts` case `KICKED`
- Test: `GameFlowIntegrationTest.secondLoginKicksTheFirstSession`

**Hay bị hỏi:** *Đơn phiên liên quan gì tới kết nối lại?* Đây chính là cơ chế kết nối lại: client mất mạng mở kết nối mới và gửi
`AUTH` bằng token cũ. Server coi đó là một lần đăng nhập mới, đá phiên cũ (nếu chưa bị phát hiện là chết) và gắn phiên mới vào trận.

---

## 2. Sảnh và tìm đối thủ

### UC05. Xem danh sách người chơi online

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Xem ai đang online, Elo và trạng thái của họ, cập nhật thời gian thực |
| **Tiền điều kiện** | Đã đăng nhập |
| **Hậu điều kiện** | Không đổi trạng thái hệ thống |
| **Thông điệp** | `LIST_ONLINE`, push `ONLINE_USERS` |

**Luồng chính**
1. Ngay sau khi đăng nhập, client gửi `LIST_ONLINE`. Server trả `{users: [OnlineUser]}` sắp theo username.
2. Trạng thái của từng người được tính trong `LobbyService.presenceOf()`:
   - `IN_QUEUE`: đang ở hàng chờ;
   - `PLAYING`: ở phòng `PLAYING`;
   - `IN_ROOM`: ở phòng `WAITING` hoặc `FINISHED`;
   - `SPECTATING`: đang xem;
   - `IDLE`: còn lại.
3. Mỗi khi có thay đổi (vào/ra, vào phòng, vào hàng chờ, trận bắt đầu/kết thúc...), `LobbyService.broadcastLobby()`
   gửi lại **toàn bộ** danh sách qua `ONLINE_USERS` cho mọi người đã đăng nhập.
4. Client thay danh sách cũ bằng danh sách mới. Từ danh sách này có thể mời người chơi (UC09) hoặc xem hồ sơ (UC26).

**Mã nguồn**
- Server: `LobbyService.listOnline()`, `onlineUsers()`, `presenceOf()`, `broadcastLobby()`, `SessionRegistry.broadcast()`
- Desktop: `ui/LobbyPanel` (bảng người online). Web: `components/Lobby.tsx`

**Hay bị hỏi:** *Gửi lại cả danh sách mỗi lần có thay đổi có tốn không?* Có, nhưng ở quy mô lớp học thì ổn. Server chỉ tạo
chuỗi JSON một lần cho mỗi lần broadcast (`SessionRegistry.broadcast`). Muốn mở rộng thì chuyển sang gửi phần thay đổi (diff).

---

### UC06. Xem danh sách phòng

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Xem các phòng đang mở: tên, trạng thái, người chơi, người xem, luật, có phải phòng bot không |
| **Tiền điều kiện** | Đã đăng nhập |
| **Hậu điều kiện** | Không đổi trạng thái |
| **Thông điệp** | `LIST_ROOMS`, push `ROOM_LIST` |

**Luồng chính**
1. Sau khi đăng nhập, client gửi `LIST_ROOMS` và nhận `{rooms: [RoomInfo]}`.
2. Mỗi khi phòng được tạo, xóa hay đổi trạng thái, server broadcast `ROOM_LIST` (cùng lúc với `ONLINE_USERS` trong `broadcastLobby()`).
3. Từ danh sách, người dùng chọn: **Vào** phòng `WAITING` (UC08) hoặc **Xem** phòng bất kỳ (UC23).

**Mã nguồn**
- Server: `LobbyService.listRooms()`, `roomInfos()`, `Room.info()`
- Desktop: `ui/LobbyPanel`. Web: `components/Lobby.tsx` (`RoomBadges` hiện nhãn luật/bot)

---

### UC07. Tạo phòng

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Tạo phòng mới, đặt tên, tùy chọn bật luật chặn hai đầu |
| **Tiền điều kiện** | Đã đăng nhập, không ở trong phòng nào với tư cách người chơi |
| **Hậu điều kiện** | Có phòng mới trạng thái `WAITING`, người tạo là chủ phòng (host) và sẽ cầm `X` ở ván đầu. Người tạo tự rời hàng chờ và thôi xem phòng khác (nếu có) |
| **Thông điệp** | `CREATE_ROOM {name?, blockTwoEnds?}` |

**Luồng chính**
1. Bấm Tạo phòng, nhập tên (có thể bỏ trống), tick "Chặn hai đầu" nếu muốn.
2. `LobbyService.createRoom()`:
   1. tên trống thì đặt "Phòng của <tên>"; tên dài hơn 40 ký tự thì báo lỗi;
   2. trong khóa lobby: `requireNotInRoom()`, `dropFromQueue()` (rời hàng chờ và nhận `QUEUE_STATUS`);
   3. `openRoom()`: `stopSpectating()`, tạo `Room` với id `R<số tăng dần>`, ghi vào `rooms` và `roomByUser`;
   4. `broadcastLobby()`.
3. Trả `{room: RoomInfo}`. Client chuyển sang màn hình phòng và chờ đối thủ.

**Luồng ngoại lệ:** đang ở phòng khác thì `ALREADY_IN_ROOM`. Tên quá 40 ký tự thì `VALIDATION_ERROR`.

**Mã nguồn**
- Server: `LobbyService.createRoom()`, `openRoom()`, `Room`
- Desktop: `GameClient.createRoom()`, `ui/LobbyPanel`. Web: `store.createRoom`, `components/Lobby.tsx`
- Test: `RoomExtrasIntegrationTest.blockTwoEndsRoomKeepsPlayingAfterABlockedFiveAndStoresTheRule`

---

### UC08. Vào phòng chờ

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Vào một phòng đang `WAITING`. Đủ 2 người thì trận tự bắt đầu |
| **Tiền điều kiện** | Đã đăng nhập, không ở phòng nào. Phòng tồn tại và đang `WAITING` |
| **Hậu điều kiện** | Phòng chuyển sang `PLAYING`. Một `GameMatch` mới được tạo: host cầm `X`, người vào cầm `O`. Đồng hồ lượt của X bắt đầu chạy |
| **Thông điệp** | `JOIN_ROOM {roomId}`, push `ROOM_UPDATE`, `MATCH_START`, `ROOM_LIST`, `ONLINE_USERS` |

**Luồng chính**
1. Chọn phòng trong danh sách và bấm Vào.
2. `LobbyService.joinRoom()` (trong khóa lobby):
   1. kiểm tra không ở phòng nào, phòng tồn tại, đang `WAITING`;
   2. `dropFromQueue()`;
   3. `seatAndStart()`: `seat()` (thôi xem nếu đang xem, thêm vào phòng), rồi `startMatch(room, hostId, guestId)`.
3. `startMatch()`:
   1. **đọc lại rating của cả hai từ DB** (`participant()` → `loadPlayer()`), vì Elo phải tính theo rating lúc trận bắt đầu;
   2. đóng mọi lời mời đang treo của hai người (lý do `UNAVAILABLE`);
   3. tạo `GameMatch` với UUID mới, gửi `ROOM_UPDATE`;
   4. `match.start()`: ghi thời điểm bắt đầu, bật đồng hồ lượt 30 giây, gửi `MATCH_START` (một `MatchState` riêng cho từng người,
      trong đó `yourMark` là quân của người nhận) cho 2 người chơi và người xem.
4. `broadcastLobby()`. Trả `{room}`.

**Luồng ngoại lệ:** `ROOM_NOT_FOUND`. `ROOM_FULL` khi phòng không còn `WAITING`. `ALREADY_IN_ROOM`.

**Mã nguồn**
- Server: `LobbyService.joinRoom()`, `seatAndStart()`, `startMatch()`, `GameMatch.start()`
- Desktop: `GameClient.joinRoom()`, `ui/LobbyPanel` → `ui/RoomPanel`. Web: `store.joinRoom`, `Lobby.tsx` → `Room.tsx`
- Test: `GameFlowIntegrationTest.tcpPlayerBeatsWebPlayerAndTheResultIsStored`, `GameMatchTest.startSendsEachPlayerTheirOwnMark`

**Hay bị hỏi:** *Hai người cùng bấm vào một phòng cùng lúc thì sao?* Cả hai đều phải vào khóa lobby. Người vào trước làm phòng
chuyển `PLAYING`. Người sau kiểm tra thấy không còn `WAITING` nên nhận `ROOM_FULL`.

---

### UC09. Thách đấu / mời người chơi

| | |
|---|---|
| **Actor** | Người chơi (người mời) |
| **Mô tả** | Gửi lời mời đấu tới một người đang online |
| **Tiền điều kiện** | Người mời: rảnh hoặc đang **một mình trong phòng chờ**, không ở hàng chờ, chưa có lời mời nào đang treo. Người được mời: online, không ở phòng nào với tư cách người chơi (đang ở hàng chờ hoặc đang xem vẫn mời được) |
| **Hậu điều kiện** | Có một `Invitation` id `I<số>`, hết hạn sau 20 giây. Người được mời nhận `INVITED` |
| **Thông điệp** | `INVITE {userId}` → `OK {inviteId, expiresAt}`, push `INVITED {inviteId, from, expiresAt}` |

**Luồng chính**
1. Chọn một người trong danh sách online (hoặc nút "Mời" trong phòng chờ) và bấm Mời.
2. `LobbyService.invite()` kiểm tra lần lượt:
   1. không tự mời chính mình;
   2. người được mời đang online;
   3. phòng của mình (nếu có) đang `WAITING`;
   4. mình không ở hàng chờ;
   5. người được mời không ở phòng nào;
   6. mình chưa có lời mời đang treo.
3. Tạo `Invitation`, hẹn giờ `expireInvite()` sau 20 giây trên scheduler.
4. Gửi `INVITED` cho người được mời. Trả `{inviteId, expiresAt}` cho người mời.
5. Desktop (`ui/Invites`) và web (`components/Popups.tsx`) hiện popup có đếm ngược theo `expiresAt`.

**Luồng ngoại lệ**
- `VALIDATION_ERROR` (tự mời mình), `USER_OFFLINE`, `ALREADY_IN_ROOM` (phòng mình không còn `WAITING`).
- `ALREADY_IN_QUEUE` (mình đang tìm trận), `USER_BUSY` (người kia đang ở phòng), `INVITE_PENDING` (đã có lời mời đang treo).
- Hết 20 giây không ai phản hồi: hai bên nhận `INVITE_CLOSED {reason: EXPIRED}`.

**Mã nguồn**
- Server: `LobbyService.invite()`, `expireInvite()`, `closeInvite()`, `Invitation`
- Desktop: `GameClient.invite()`, `ui/Invites`, `ui/LobbyPanel`. Web: `store.invite()`, `Lobby.tsx` (`InviteButton`), `Room.tsx` (`InvitePicker`), `Popups.tsx`
- Test: `LobbyFeaturesIntegrationTest.invitationCanBeDeclinedCancelledOrExpire`

**Hay bị hỏi:** *Hết hạn lời mời làm thế nào?* Dùng `ScheduledExecutorService.schedule()`. Khi hẹn giờ chạy, `expireInvite()`
vào khóa lobby và kiểm tra lời mời còn trong map không. Nếu lời mời đã được trả lời trước đó thì không làm gì.
Khi đóng lời mời, timer cũng bị hủy (`cancelTimer()`).

---

### UC10. Trả lời thách đấu

| | |
|---|---|
| **Actor** | Người chơi (người được mời) |
| **Mô tả** | Đồng ý hoặc từ chối lời mời |
| **Tiền điều kiện** | Có lời mời đang treo gửi tới mình |
| **Hậu điều kiện** | Đồng ý: hai người vào cùng một phòng, **người mời cầm X**, trận bắt đầu ngay. Từ chối: lời mời bị xóa |
| **Thông điệp** | `INVITE_REPLY {inviteId, accept}`, push `INVITE_CLOSED` cho người mời |

**Luồng chính (đồng ý)**
1. Bấm Đồng ý trên popup. `LobbyService.acceptInvite()`:
   1. `requireInviteTo()`: lời mời tồn tại và đúng là gửi cho mình;
   2. `requireNotInRoom()`;
   3. kiểm tra người mời vẫn sẵn sàng: còn online, không ở hàng chờ, không có phòng hoặc phòng đang `WAITING`.
2. Đóng lời mời (người mời nhận `INVITE_CLOSED {ACCEPTED}`), `dropFromQueue()` cho người được mời.
3. Người mời đang có phòng chờ thì dùng phòng đó, nếu không thì `openRoom()` mới với người mời làm host.
4. `seatAndStart()` → `startMatch(room, hostId, guestId)`: host (người mời) cầm X. Gửi `MATCH_START`.
5. Trả `{room}`.

**Luồng chính (từ chối)**
1. Bấm Từ chối. `declineInvite()` đóng lời mời, người mời nhận `INVITE_CLOSED {DECLINED}`. Trả `{}`.

**Luồng ngoại lệ**
- `INVITE_NOT_FOUND`: lời mời không tồn tại, đã hết hạn, hoặc người mời không còn sẵn sàng (khi đó người mời nhận `INVITE_CLOSED {UNAVAILABLE}`).
- `ALREADY_IN_ROOM`.

**Mã nguồn**
- Server: `LobbyService.acceptInvite()`, `declineInvite()`, `requireInviteTo()`
- Desktop: `GameClient.acceptInvite()`/`declineInvite()`, `ui/Invites`. Web: `store.answerInvite()`, `Popups.tsx`
- Test: `LobbyFeaturesIntegrationTest.acceptedInvitationStartsAMatchWithTheInviterAsX`, `acceptingAnInvitationLeavesTheQueue`

---

### UC11. Hủy lời mời

| | |
|---|---|
| **Actor** | Người chơi (người mời) |
| **Mô tả** | Rút lại lời mời khi đối phương chưa trả lời |
| **Tiền điều kiện** | Có lời mời mình gửi đi đang treo |
| **Hậu điều kiện** | Lời mời bị xóa. Người được mời nhận `INVITE_CLOSED {CANCELLED}` và popup biến mất |
| **Thông điệp** | `CANCEL_INVITE {inviteId}` |

**Luồng chính**
1. Bấm Hủy lời mời. `LobbyService.cancelInvite()` kiểm tra lời mời tồn tại và mình là người gửi.
2. `closeInvite(..., CANCELLED, tellInviter=false, tellInvitee=true)`: hủy timer, xóa khỏi map, báo người được mời.

**Các trường hợp lời mời tự đóng:**
- người mời vào hàng chờ (`joinQueue`): `CANCELLED`;
- một trong hai người mất kết nối hoặc bắt đầu trận khác (`closeInvitesOf`): `UNAVAILABLE`;
- hết hạn 20 giây: `EXPIRED`.

**Luồng ngoại lệ:** `INVITE_NOT_FOUND`.

**Mã nguồn**
- Server: `LobbyService.cancelInvite()`, `closeInvite()`, `closeInvitesOf()`
- Desktop: `GameClient.cancelInvite()`. Web: `store.cancelInvite()`
- Test: `LobbyFeaturesIntegrationTest.invitationCanBeDeclinedCancelledOrExpire`, `invitationIsClosedWhenTheInvitedPlayerGoesOffline`

---

### UC12. Tìm trận nhanh

| | |
|---|---|
| **Actor** | Người chơi, Hệ thống (bộ ghép trận) |
| **Mô tả** | Vào hàng chờ, server tự ghép với người có Elo gần nhất. Khoảng chênh lệch chấp nhận được nới dần theo thời gian chờ |
| **Tiền điều kiện** | Đã đăng nhập, không ở phòng nào, chưa ở hàng chờ |
| **Hậu điều kiện** | Ghép được thì server tự tạo phòng "Ghép trận ngẫu nhiên", **bốc thăm** người cầm X, trận bắt đầu ngay |
| **Thông điệp** | `QUICK_MATCH` → `OK QueueStatus`, `CANCEL_QUICK_MATCH`, push `QUEUE_STATUS`, `MATCH_START` |

**Luồng chính**
1. Bấm Tìm trận. `LobbyService.joinQueue()`:
   1. kiểm tra không ở phòng, chưa ở hàng chờ;
   2. lời mời mình đang gửi (nếu có) bị hủy;
   3. `queue.add()` lưu thời điểm vào;
   4. **thử ghép luôn** bằng `matchWaitingPlayers()`.
2. Mỗi giây scheduler gọi `matchmakingTick()` để thử ghép lại, và gửi `QUEUE_STATUS` cho những ai vừa được nới khoảng.
3. Khoảng chấp nhận (`MatchmakingQueue.rangeAfter()`):
   - mới vào: ±100;
   - cứ mỗi 5 giây chờ: thêm ±50;
   - từ giây thứ 30: `null`, nghĩa là ghép với bất kỳ ai.
4. `MatchmakingQueue.takePairs()`:
   1. duyệt theo thứ tự vào hàng chờ, người chờ lâu nhất được xét trước;
   2. với mỗi người A, tìm người B có chênh lệch Elo **nhỏ nhất** sao cho chênh lệch nằm trong khoảng của **cả A lẫn B**;
   3. bỏ cả hai khỏi hàng chờ.
5. Mỗi cặp ghép được:
   1. bốc thăm X/O bằng `ThreadLocalRandom`;
   2. gửi `QUEUE_STATUS {inQueue: false}`;
   3. `openRoom()`, `seat()`, `startMatch()`, gửi `MATCH_START`.
6. Hủy tìm: `CANCEL_QUICK_MATCH` → `leaveQueue()`.

**Luồng ngoại lệ**
- `ALREADY_IN_ROOM`, `ALREADY_IN_QUEUE`, `NOT_IN_QUEUE` (hủy khi không ở hàng chờ).
- Mất kết nối, đăng xuất hoặc bị khóa: tự rời hàng chờ (`onUserDisconnected`).
- Vào phòng bằng cách khác (tạo phòng, vào phòng, nhận lời mời, chơi bot): tự rời hàng chờ (`dropFromQueue`).

**Mã nguồn**
- Server: `LobbyService.joinQueue()`, `leaveQueue()`, `matchmakingTick()`, `matchWaitingPlayers()`, `MatchmakingQueue`
- Desktop: `GameClient.quickMatch()`/`cancelQuickMatch()`, `ui/LobbyPanel`. Web: `store.quickMatch()`, `Lobby.tsx` (`QuickMatch`)
- Test: `MatchmakingQueueTest`, `LobbyFeaturesIntegrationTest.quickMatchPairsTwoWaitingPlayers`, `cancellingTheQueue`

**Hay bị hỏi:** *Vì sao phải nằm trong khoảng của cả hai?* Người mới vào chỉ chấp nhận ±100. Nếu chỉ xét khoảng của người chờ lâu
(đã nới rộng) thì người mới có thể bị ghép với đối thủ chênh quá xa so với mong muốn của họ.

---

### UC13. Đánh với máy (Bot)

| | |
|---|---|
| **Actor** | Người chơi, Bot |
| **Mô tả** | Chơi với bot chạy trong server, mức `EASY` hoặc `HARD` (mặc định), có thể bật chặn hai đầu |
| **Tiền điều kiện** | Đã đăng nhập, không ở phòng nào |
| **Hậu điều kiện** | Có phòng riêng (`botLevel` khác null), người chơi cầm X ở ván đầu, trận bắt đầu ngay. Trận **không tính Elo** và **không lưu** vào DB |
| **Thông điệp** | `PLAY_BOT {level?, blockTwoEnds?}` |

**Luồng chính**
1. Chọn mức, tick luật nếu muốn, bấm Chơi với máy.
2. `LobbyService.playBot()`:
   1. `openRoom()` với tên "Chơi với dễ/khó";
   2. `room.addPlayer(BotPlayer.info(level))` (bot có id `-1`, rating 1000);
   3. `startMatch(room, userId, BotPlayer.ID)`.
3. Trong `startMatch()`:
   1. tạo `BotPlayer`;
   2. **bọc `Notifier`** (`bot.wrap()`): sự kiện gửi cho id `-1` được chuyển cho bot, còn lại gửi cho người thật như bình thường;
   3. `bot.attach(match)`.
4. Bot nhận `MATCH_START`, `MOVE_APPLIED` hoặc `OPPONENT_RECONNECTED` thì lên lịch `think()` sau **0,5 giây** trên scheduler.
5. `think()`:
   1. lấy ảnh chụp `snapshotFor(-1)`, giống hệt `MatchState` mà client nhận;
   2. nếu tới lượt mình: `BotBrain.chooseMove()` chấm điểm mọi ô trống cạnh quân đã có
      (điểm tấn công + trọng số × điểm phòng thủ, kèm một chút ngẫu nhiên);
   3. `HARD` chọn ô điểm cao nhất (trọng số phòng thủ 0,9), `EASY` chọn ngẫu nhiên trong vài ô tốt nhất (trọng số 0,5);
   4. gọi `match.move(-1, x, y)`, **cùng luật** như người thật.
6. Trận kết thúc: `rated = false`, nên `ratingChange` bằng 0 và `onMatchEnded()` không gọi `persistence.save()`.

**Luồng ngoại lệ:** `ALREADY_IN_ROOM`. Bot nhận `DRAW_OFFERED` thì luôn từ chối (UC19). Chơi lại thì bot đồng ý ngay (UC22).

**Mã nguồn**
- Server: `LobbyService.playBot()`, `startMatch()`, `BotPlayer` (`wrap`, `onEvent`, `think`), `BotBrain.chooseMove()`
- Desktop: `GameClient.playBot()`, `ui/LobbyPanel`. Web: `store.playBot`, `Lobby.tsx` (`PlayBot`)
- Test: `BotBrainTest`, `RoomExtrasIntegrationTest.playingAgainstTheBotIsUnratedAndRematchSwapsMarks`, `hardBotWinsAgainstAPlayerWhoIgnoresIt`

**Hay bị hỏi:** *Vì sao bot không đi ngay khi nhận sự kiện?* Sự kiện được gửi ra **trong khi** `GameMatch` đang giữ khóa.
Nếu bot gọi `move()` ngay trong đó thì sẽ chạy lồng vào giữa nước đi trước. Vì vậy bot chỉ lên lịch trên scheduler, đúng như một client ở xa.

---

## 3. Trận đấu và trọng tài

### UC14. Thực hiện nước đi

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Đặt quân tại ô (x, y). Server kiểm tra hợp lệ rồi mới áp dụng |
| **Tiền điều kiện** | Là người chơi của trận (`matchId` đúng), trận `IN_PROGRESS`, đang tới lượt mình |
| **Hậu điều kiện** | Quân được đặt (UC16), lượt chuyển sang đối thủ, đồng hồ lượt khởi động lại (UC15). Nước đi tạo thắng/hòa thì trận kết thúc (UC17) |
| **Thông điệp** | `MOVE {matchId, x, y}` → `OK {moveNo}`, push `MOVE_APPLIED` |

**Luồng chính**
1. Người chơi bấm vào ô trống trên bàn. Client chỉ gửi ý định, **không** tự vẽ quân:
   - desktop: `BoardPanel` → `GameClient.move()`;
   - web: `Board.tsx` → `store.move()`.
2. `LobbyService.move()` gọi `requireMatch()` (trong khóa lobby: trận hiện tại của user phải có id đúng `matchId`),
   rồi **nhả khóa lobby** và gọi `GameMatch.move()`.
3. `GameMatch.move()` trong `synchronized (this)` kiểm tra lần lượt:
   1. là người chơi của trận (`requireMark`);
   2. `status == IN_PROGRESS`;
   3. đúng lượt;
   4. tọa độ trong bàn;
   5. ô còn trống.
4. Hợp lệ thì chuyển sang UC16 (cập nhật bàn) và UC17 (kiểm tra thắng/hòa).
5. Trả `OK {moveNo}`. Hai client vẽ quân khi nhận `MOVE_APPLIED`, không vẽ khi nhận `OK`.

**Luồng ngoại lệ:** `MATCH_NOT_FOUND`, `MATCH_NOT_IN_PROGRESS` (đã kết thúc hoặc đang tạm dừng), `NOT_YOUR_TURN`, `INVALID_POSITION`, `CELL_OCCUPIED`.

**Mã nguồn**
- Server: `MessageDispatcher` case `MOVE`, `LobbyService.move()`, `requireMatch()`, `GameMatch.move()`, `Board`
- Desktop: `ui/BoardPanel`, `GameClient.move()`. Web: `components/Board.tsx`, `store.move()`
- Test: `GameMatchTest.enforcesTurnOrder`, `rejectsInvalidCells`, `GameFlowIntegrationTest.protocolErrorsAreReported`

**Hay bị hỏi**
- *Vì sao `GameMatch.move()` gọi ngoài khóa lobby?* Để nước đi ở các phòng khác nhau không phải chờ nhau. Khóa lobby chỉ giữ
  trong lúc tìm trận, sau đó chỉ khóa đúng trận đó.
- *Client sửa code để gửi nước đi sai lượt thì sao?* Server là nguồn sự thật. Mọi kiểm tra nằm trong `GameMatch`, nên server
  trả `NOT_YOUR_TURN` và không có gì thay đổi.

---

### UC15. Đếm giờ lượt đi

| | |
|---|---|
| **Actor** | Hệ thống |
| **Mô tả** | Mỗi lượt có tối đa 30 giây. Hết giờ thì người đang tới lượt thua (lý do `TIMEOUT`) |
| **Tiền điều kiện** | Trận đang `IN_PROGRESS` |
| **Hậu điều kiện** | Có nước đi kịp thì đồng hồ khởi động lại cho lượt sau. Hết giờ thì trận kết thúc |
| **Thông điệp** | Nội bộ. Client biết hạn qua `turnDeadline` trong `MATCH_START`, `MOVE_APPLIED`, `MATCH_STATE` |

**Luồng chính**
1. Mỗi khi bắt đầu lượt (`start()`, sau mỗi nước đi, sau khi kết nối lại), `restartTurnTimer(delay)`:
   1. hủy timer cũ;
   2. đặt `turnDeadline = now + delay`;
   3. **tăng `turnTimerGeneration`**;
   4. hẹn `onTurnTimeout(generation)` sau `delay` trên scheduler.
2. Khi timer chạy, `onTurnTimeout(gen)` vào khóa trận và chỉ xử lý nếu trận còn `IN_PROGRESS` **và** `gen` bằng generation hiện tại.
   Khi đó `finish(winOf(turn.opposite()), TIMEOUT)`.
3. Client tự vẽ đồng hồ đếm ngược theo `turnDeadline`. Đồng hồ máy client có thể lệch với server, nên client bù độ lệch
   bằng `serverTime` trong `PONG` (`serverNow()`).

**Luồng ngoại lệ:** trận tạm dừng (UC21) thì đồng hồ dừng và lưu lại thời gian còn lại (`pausedTimeLeftMs`).

**Mã nguồn**
- Server: `GameMatch.restartTurnTimer()`, `onTurnTimeout()`, `turnTimerGeneration`
- Desktop: `app/MatchView`, `ui/RoomPanel`, `Connection.serverNow()`. Web: `Room.tsx`, `socket.serverNow()`
- Test: `GameMatchTest.turnTimeoutLosesForThePlayerOnTurn`, `staleTimeoutIsIgnored`

**Hay bị hỏi:** *Số generation để làm gì?* Có thể timer đã bắt đầu chạy (đang chờ lấy khóa trận) đúng lúc một nước đi hợp lệ tới.
`cancel()` không dừng được tác vụ đã bắt đầu. Nước đi tăng generation, nên timeout cũ thấy generation không khớp và bị bỏ qua,
người chơi không bị xử thua oan.

---

### UC16. Cập nhật bàn cờ

| | |
|---|---|
| **Actor** | Hệ thống |
| **Mô tả** | Ghi nước đi, đổi lượt và đồng bộ cho cả hai người chơi và mọi người xem |
| **Tiền điều kiện** | Nước đi đã qua kiểm tra ở UC14 |
| **Hậu điều kiện** | Bàn cờ trong RAM có thêm quân. Danh sách `moves` có thêm `PlayedMove`. Mọi người trong trận có cùng trạng thái |
| **Thông điệp** | Push `MOVE_APPLIED {matchId, moveNo, x, y, mark, nextTurn, turnDeadline}` |

**Luồng chính** (vẫn trong khóa trận, tiếp theo UC14)
1. `board.place(x, y, mark)`. `moveNo = moves.size() + 1`. Thêm `PlayedMove` có thời điểm.
2. Kiểm tra thắng/hòa (UC17). Nếu chưa kết thúc thì `turn = turn.opposite()` và `restartTurnTimer()`.
3. `broadcast(MOVE_APPLIED)` tới `audience()`, gồm X, O và người xem.
   Nếu trận kết thúc bằng nước này thì `nextTurn` và `turnDeadline` là `null`.
4. Có lời cầu hòa đang treo mà trận chưa kết thúc thì hủy lời cầu hòa và gửi `DRAW_OFFER_CLOSED {MOVED}` (UC19).
5. Client nhận `MOVE_APPLIED` thì đặt quân, đánh dấu nước cuối, đổi lượt, đặt lại đồng hồ.
   Logic phía web nằm ở `state/match.ts` (có test riêng).

**Mã nguồn**
- Server: `GameMatch.move()`, `broadcast()`, `audience()`, `Board`, `MoveApplied`
- Desktop: `app/BoardModel`, `app/MatchView`, `GameClient.handleEvent()`. Web: `state/match.ts`, `store.ts` case `MOVE_APPLIED`
- Test: `match.test.ts` (web), `GameMatchTest.spectatorsGetTheEventsButCannotPlay`

**Hay bị hỏi:** *Kết nối lại hoặc người xem vào giữa chừng thì lấy bàn cờ ở đâu?* Từ `MATCH_STATE`: một ảnh chụp đầy đủ
(`buildSnapshot`), với `board` là 15 chuỗi, mỗi chuỗi là một hàng.

---

### UC17. Phán quyết thắng / hòa

| | |
|---|---|
| **Actor** | Hệ thống |
| **Mô tả** | Sau mỗi nước đi, kiểm tra 5 quân liên tiếp (có xét luật chặn hai đầu nếu phòng bật) hoặc bàn đầy |
| **Tiền điều kiện** | Vừa có một nước đi hợp lệ |
| **Hậu điều kiện** | Có người thắng: kết thúc với `X_WIN`/`O_WIN` và lý do `FIVE_IN_ROW`, kèm `winLine`. Đủ 225 ô: `DRAW`/`BOARD_FULL`. Hòa do đồng ý (`AGREED`) xem UC19 |
| **Thông điệp** | Push `MATCH_END` (`MatchResult`) |

**Luồng chính**
1. `RuleEngine.findWinLine(board, x, y, 5, blockTwoEnds)` **chỉ xét 4 đường đi qua nước vừa đánh**:
   ngang, dọc và hai đường chéo. Những đường không đi qua nước vừa đánh không thể vừa trở thành đường thắng.
2. Với mỗi hướng, `collect()` đếm các quân cùng màu liên tiếp về hai phía. Từ 5 quân trở lên là thắng, **trừ khi** phòng bật
   chặn hai đầu và `blockedAtBothEnds()` đúng: ô ngay sau **cả hai đầu** dãy đều là quân đối thủ.
   Mép bàn không tính là bị chặn.
3. Không có đường thắng mà `board.isFull()` thì hòa.
4. `GameMatch.finish(type, reason, winLine)`:
   1. đặt `FINISHED`, hủy mọi timer;
   2. tính Elo nếu trận có tính điểm (UC37);
   3. tạo `MatchResult`, broadcast `MATCH_END`.
5. **Sau khi nhả khóa trận**, `fireEndedIf()` gọi `LobbyService.onMatchEnded()`:
   1. lưu DB (UC37);
   2. cập nhật rating mới trong danh sách online và trong phòng;
   3. người đã offline bị bỏ khỏi phòng;
   4. phòng chuyển `FINISHED`, gửi `ROOM_UPDATE`, `broadcastLobby()`.
6. Client tô `winLine` và hiện kết quả cùng điểm thay đổi.

**Mã nguồn**
- Server: `RuleEngine`, `GameMatch.move()`, `finish()`, `fireEndedIf()`, `LobbyService.onMatchEnded()`
- Desktop: `GameClient.matchEnded()`, `ui/RoomPanel`, `ui/BoardPanel`. Web: `Room.tsx`, `Board.tsx` (`winLine`)
- Test: `RuleEngineTest`, `GameMatchTest.fiveInARowWinsAndTheMatchEndsExactlyOnce`, `fullBoardIsADraw`, `blockTwoEndsRuleLetsTheMatchGoOn`

**Hay bị hỏi**
- *Làm sao đảm bảo một trận chỉ kết thúc một lần?* Nước thắng, hết giờ và đầu hàng có thể xảy ra gần như cùng lúc.
  `finish()` luôn chạy trong khóa trận và kiểm tra `if (status == FINISHED) return false`, nên chỉ lần gọi đầu tiên có tác dụng.
  DB cũng chặn thêm một lớp: khóa chính của `matches` là `matchId`.
- *Vì sao `onMatchEnded` phải gọi sau khi nhả khóa trận?* `onMatchEnded` cần khóa lobby. Nếu gọi khi còn giữ khóa trận thì
  thứ tự là match → lobby, ngược với chỗ khác (lobby → match), và hai luồng có thể chờ nhau mãi (deadlock).

---

### UC18. Đầu hàng

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Chịu thua giữa ván |
| **Tiền điều kiện** | Là người chơi của trận, trận chưa kết thúc (đang `PAUSED` vẫn đầu hàng được) |
| **Hậu điều kiện** | Đối thủ thắng, lý do `RESIGN`, Elo được tính |
| **Thông điệp** | `RESIGN {matchId}`, push `MATCH_END` |

**Luồng chính**
1. Bấm Đầu hàng, client hỏi xác nhận (web dùng `Confirm.tsx`), rồi gửi `RESIGN`.
2. `LobbyService.resign()` → `requireMatch()` → `GameMatch.resign()`: `finish(winOf(mark.opposite()), RESIGN)`.
3. `MATCH_END` tới mọi người, tiếp theo là `onMatchEnded()` như UC17.

**Luồng ngoại lệ:** `MATCH_NOT_FOUND`, `MATCH_NOT_IN_PROGRESS` (trận đã kết thúc).

**Mã nguồn**
- Server: `GameMatch.resign()`
- Desktop: `GameClient.resign()`, `ui/RoomPanel`. Web: `store.resign()`, `Room.tsx`
- Test: `GameMatchTest.resignGivesTheWinToTheOpponent`

---

### UC19. Cầu hòa

| | |
|---|---|
| **Actor** | Người chơi (người đề nghị và đối thủ) |
| **Mô tả** | Đề nghị hòa giữa ván. Đối thủ đồng ý thì hòa, từ chối thì chơi tiếp |
| **Tiền điều kiện** | Trận `IN_PROGRESS`. Không có lời cầu hòa nào đang treo. Nếu mình vừa bị từ chối thì phải có ít nhất một nước đi mới |
| **Hậu điều kiện** | Đồng ý: `DRAW` lý do `AGREED`, Elo tính như hòa. Từ chối: trận tiếp tục |
| **Thông điệp** | `OFFER_DRAW {matchId}`, `ANSWER_DRAW {matchId, accept}`, push `DRAW_OFFERED {fromId}`, `DRAW_OFFER_CLOSED {reason}` |

**Luồng chính**
1. A bấm Cầu hòa (không cần đang tới lượt). `GameMatch.offerDraw()`:
   1. kiểm tra trận `IN_PROGRESS`, `drawOfferFrom == null`;
   2. kiểm tra `lastDrawOfferAt[A]` khác số nước hiện tại (chống spam);
   3. ghi `drawOfferFrom = A`;
   4. gửi `DRAW_OFFERED` cho mọi người trừ A.
2. B thấy hộp hỏi Đồng ý / Từ chối. Đồng hồ lượt vẫn chạy.
3. B trả lời bằng `GameMatch.answerDraw()`:
   - đồng ý: `finish(DRAW, AGREED)`, gửi `MATCH_END`;
   - từ chối: xóa lời cầu hòa, gửi `DRAW_OFFER_CLOSED {DECLINED}` cho mọi người trừ B.
4. Nếu có người đi một nước trong lúc lời cầu hòa đang treo thì lời cầu hòa tự hủy: `DRAW_OFFER_CLOSED {MOVED}`.
5. Người kết nối lại thấy lời cầu hòa đang treo qua `MatchState.drawOfferFrom`.

**Luồng ngoại lệ**
- `DRAW_OFFER_NOT_ALLOWED`: đang có lời cầu hòa treo, hoặc vừa cầu hòa mà chưa có nước đi mới.
- `NO_DRAW_OFFER`: trả lời khi không có lời cầu hòa nào của đối thủ.
- `MATCH_NOT_IN_PROGRESS`.
- Bot luôn từ chối (`BotPlayer.declineDraw()`). Người xem không cầu hòa được (`requireMark` thất bại).

**Mã nguồn**
- Server: `GameMatch.offerDraw()`, `answerDraw()`, đoạn cuối `move()`, migration `V3__agreed_draw.sql` (thêm `AGREED` vào ràng buộc CHECK)
- Desktop: `GameClient.offerDraw()`/`answerDraw()`, `ui/RoomPanel`. Web: `store.offerDraw()`/`answerDraw()`, `Room.tsx` (`DrawControls`)
- Test: `GameMatchTest.drawOfferCanBeDeclinedClosedByAMoveOrAccepted`, `RoomExtrasIntegrationTest.drawByAgreementIsStoredAndTheBotDeclines`

---

### UC20. Thoát / rời phòng giữa ván

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Rời phòng. Nếu trận đang chạy thì tính như đầu hàng |
| **Tiền điều kiện** | Đang ở trong phòng với tư cách người chơi |
| **Hậu điều kiện** | Trận đang chạy: đối thủ thắng (`RESIGN`). Người rời ra sảnh. Phòng còn người thì người còn lại thành host và phòng về `WAITING`. Phòng hết người (không tính bot) thì bị xóa, người xem nhận `SPECTATE_ENDED` |
| **Thông điệp** | `LEAVE_ROOM`, push `MATCH_END`, `ROOM_UPDATE`, `ROOM_LIST` |

**Luồng chính**
1. Bấm Rời phòng (client hỏi xác nhận nếu đang đấu). `LobbyService.leaveRoom()` → `removeFromRoom(room, userId)`:
   1. `match.forfeitIfRunning(userId)`: nếu trận chưa xong thì `finish(..., RESIGN)`;
   2. `room.removePlayer()`, xóa khỏi `roomByUser`;
   3. phòng trống thì `deleteRoom()`, ngược lại gửi `ROOM_UPDATE`.
2. `broadcastLobby()`. Trả `{}`.

**Luồng ngoại lệ:** không ở phòng nào và cũng không xem phòng nào thì `NOT_IN_ROOM`. Người xem gửi `LEAVE_ROOM` là thôi xem (UC24).

**Mã nguồn**
- Server: `LobbyService.leaveRoom()`, `removeFromRoom()`, `deleteRoom()`, `GameMatch.forfeitIfRunning()`, `Room.removePlayer()`
- Desktop: `GameClient.leaveRoom()`, `ui/RoomPanel`. Web: `store.leaveRoom()`, `Room.tsx`

**Hay bị hỏi:** *Rời phòng khác gì mất mạng?* Rời phòng là chủ động nên thua ngay. Mất mạng thì trận tạm dừng và chờ 30 giây (UC21).

---

### UC21. Xử lý mất kết nối và kết nối lại

| | |
|---|---|
| **Actor** | Người chơi, Hệ thống |
| **Mô tả** | Mất mạng giữa ván thì trận tạm dừng 30 giây chờ người đó quay lại |
| **Tiền điều kiện** | Đang trong một trận chưa kết thúc |
| **Hậu điều kiện** | Quay lại kịp: trận tiếp tục với thời gian lượt còn lại. Quá 30 giây: người mất kết nối thua (`DISCONNECT`). Cả hai cùng mất: trận bị hủy (`ABORTED`/`BOTH_DISCONNECTED`), không tính điểm |
| **Thông điệp** | Push `OPPONENT_DISCONNECTED {userId, graceDeadline}`, `OPPONENT_RECONNECTED {userId, turnDeadline}`, `MATCH_STATE`, `ROOM_UPDATE`. Client gửi `AUTH`, `GET_STATE` |

**Luồng chính: phát hiện mất kết nối**
1. Kết nối bị đóng hoặc lỗi (TCP đọc ra `-1`/`IOException`, WebSocket `afterConnectionClosed`), hoặc heartbeat quá 30 giây (UC35).
   Khi đó `MessageDispatcher.onClose()` chạy.
2. `onClose()` chỉ cập nhật trạng thái, không tự đóng kết nối. Nếu đây là phiên đang hoạt động của user thì gọi
   `LobbyService.onUserDisconnected()`:
   1. bỏ khỏi online và hàng chờ, thôi xem, đóng lời mời (`UNAVAILABLE`);
   2. nếu đang trong trận chưa xong thì `GameMatch.playerDisconnected()`, ngược lại `removeFromRoom()`.
3. `playerDisconnected()`:
   - nếu người kia **cũng đang mất kết nối** thì `finish(ABORTED, BOTH_DISCONNECTED)`;
   - nếu không thì:
     1. lưu `pausedTimeLeftMs`, hủy đồng hồ lượt, tăng generation;
     2. `status = PAUSED`;
     3. hẹn `onGraceExpired()` sau 30 giây;
     4. gửi `OPPONENT_DISCONNECTED` cho đối thủ và người xem.

**Luồng chính: kết nối lại**
1. Client thấy mất kết nối thì tự kết nối lại:
   - desktop: `GameClient.connectionClosed()` → `scheduleReconnect()`;
   - web: logic tương tự trong `store.ts`.
   Cứ 2 giây thử một lần, trong tối đa 30 giây.
2. Kết nối được thì gửi `AUTH {token}` (`resumeSession()`), rồi `completeLogin()`. Nếu phiên cũ còn sống thì bị đá (UC04).
3. `LobbyService.onUserConnected()` gửi lại `ROOM_UPDATE`, sau đó `GameMatch.playerReconnected()`:
   1. hủy grace timer, `status = IN_PROGRESS`;
   2. `restartTurnTimer(pausedTimeLeftMs)`: đồng hồ chạy tiếp từ thời gian còn lại;
   3. gửi `OPPONENT_RECONNECTED` cho người kia;
   4. gửi `MATCH_STATE` đầy đủ cho người vừa quay lại.
4. Client gọi thêm `GET_STATE` (`synchronizeState()`) để lấy lại phòng, trận, hàng chờ.
   Người xem bị mất kết nối thì client tự gửi lại `SPECTATE`.

**Luồng ngoại lệ**
- Hết 30 giây: `onGraceExpired()` → `finish(winOf(đối thủ), DISCONNECT)`.
- Token hết hạn hoặc tài khoản bị khóa khi kết nối lại: `UNAUTHORIZED`/`ACCOUNT_LOCKED`, client về màn hình đăng nhập.
- Người ở phòng `WAITING`/`FINISHED` mà mất kết nối thì bị rời phòng luôn, không chờ.

**Mã nguồn**
- Server: `MessageDispatcher.onClose()`, `LobbyService.onUserDisconnected()`, `onUserConnected()`, `GameMatch.playerDisconnected()`,
  `playerReconnected()`, `onGraceExpired()`, `LobbyService.getState()`
- Desktop: `GameClient.connectionClosed()`, `scheduleReconnect()`, `resumeSession()`, `synchronizeState()`. Web: `store.ts` (reconnect, `GET_STATE`)
- Test: `GameMatchTest.disconnectPausesAndReconnectResumes`, `graceExpiryLosesForTheDisconnectedPlayer`, `bothDisconnectedAbortsWithoutRatingChange`,
  `GameFlowIntegrationTest.disconnectedPlayerCanComeBackAndContinue`

**Hay bị hỏi**
- *Vì sao `onClose()` không đóng kết nối?* Việc đóng là của tầng truyền tải. Tách ra như vậy thì cùng một `onClose()` dùng được
  cho TCP, WebSocket lẫn heartbeat timeout, và không gọi đóng hai lần. `markReleased()` đảm bảo `onClose()` chỉ chạy một lần mỗi phiên.
- *Demo thế nào?* Rút dây mạng hoặc tắt client giữa trận. Bên kia thấy "Đối thủ mất kết nối, chờ 30 giây".
  Mở lại client và đăng nhập thì trận chạy tiếp.

---

### UC22. Chơi lại (Rematch)

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Sau ván, cả hai cùng bấm Chơi lại thì ván mới bắt đầu, **đổi quân** cho nhau |
| **Tiền điều kiện** | Phòng đang `FINISHED`, còn đủ 2 người chơi |
| **Hậu điều kiện** | Khi cả hai đã bấm: `GameMatch` mới, người cầm O ván trước thành X. Luật chặn hai đầu giữ nguyên |
| **Thông điệp** | `REMATCH` → `OK {room}`, push `ROOM_UPDATE` (trường `rematchRequests`), `MATCH_START` |

**Luồng chính**
1. A bấm Chơi lại. `LobbyService.requestRematch()` kiểm tra phòng `FINISHED`, thêm A vào `rematchRequests`, gửi `ROOM_UPDATE`.
   B thấy "Đối thủ muốn chơi lại".
2. Phòng bot thì bot được thêm vào `rematchRequests` ngay.
3. B bấm Chơi lại. `room.requestRematch()` trả `true` (đủ hai người), nên gọi `startMatch(room, last.playerO().id(), last.playerX().id())`
   để đổi quân. Rating được đọc lại từ DB.
4. Gửi `MATCH_START`, `broadcastLobby()`.
5. Không muốn chơi lại thì rời phòng (UC20). Khi đó các yêu cầu chơi lại bị xóa.

**Luồng ngoại lệ:** `NOT_IN_ROOM`. `REMATCH_NOT_AVAILABLE` khi phòng không ở trạng thái `FINISHED`.

**Mã nguồn**
- Server: `LobbyService.requestRematch()`, `Room.requestRematch()`, `startMatch()`
- Desktop: `GameClient.rematch()`, `ui/RoomPanel`. Web: `store.rematch`, `Room.tsx`
- Test: `LobbyFeaturesIntegrationTest.rematchStartsWhenBothAskAndSwapsMarks`

---

## 4. Phòng và người xem

### UC23. Xem trận đấu

| | |
|---|---|
| **Actor** | Người xem |
| **Mô tả** | Vào xem một phòng đang mở, thấy bàn cờ, đồng hồ, kết quả theo thời gian thực |
| **Tiền điều kiện** | Đã đăng nhập, không ngồi chơi trong phòng nào. Phòng tồn tại, chưa đủ 10 người xem |
| **Hậu điều kiện** | Có tên trong `spectators` của phòng, trạng thái online là `SPECTATING`. Nhận mọi sự kiện của trận. Mỗi người chỉ xem một phòng: xem phòng mới thì tự rời phòng cũ |
| **Thông điệp** | `SPECTATE {roomId}`, push `ROOM_UPDATE`, `MATCH_STATE` (với `yourMark = null`), sau đó các sự kiện của trận |

**Luồng chính**
1. Bấm Xem ở một phòng trong danh sách. `LobbyService.spectate()`:
   1. kiểm tra mình không ngồi chơi ở phòng nào, phòng tồn tại;
   2. nếu chưa xem phòng này: kiểm tra chưa đủ 10 người, `stopSpectating()` phòng cũ, `room.addSpectator()`, ghi `spectating`;
   3. gửi `ROOM_UPDATE` cho mọi người trong phòng.
2. Phòng đang có trận hoặc vừa đấu xong thì `match.addSpectator(userId)`: thêm vào `audience()` và gửi `MATCH_STATE`
   **trước** `OK`. Vì `yourMark = null`, client vẽ bàn ở chế độ chỉ xem.
3. `broadcastLobby()`. Trả `{room}`.
4. Từ đó người xem nhận `MOVE_APPLIED`, `MATCH_END`, `DRAW_OFFERED`... như người chơi. Khi chơi lại, `startMatch()` truyền
   `room.spectatorIds()` vào trận mới nên người xem vẫn tiếp tục xem.

**Luồng ngoại lệ**
- `ALREADY_IN_ROOM`: đang ngồi chơi ở phòng khác.
- `ROOM_NOT_FOUND`. `ROOM_FULL`: đã đủ 10 người xem.
- Người xem gửi `MOVE`/`RESIGN`/`REMATCH`: `MATCH_NOT_FOUND` hoặc `NOT_IN_ROOM`, vì họ không có trong `roomByUser`.

**Mã nguồn**
- Server: `LobbyService.spectate()`, `stopSpectating()`, `GameMatch.addSpectator()`, `Room.addSpectator()`
- Desktop: `GameClient.spectate()`, `ui/LobbyPanel`, `ui/RoomPanel`. Web: `store.spectate`, `Lobby.tsx`, `Room.tsx` (`watching`)
- Test: `RoomExtrasIntegrationTest.spectatorWatchesTheMatchAndEveryoneCanChat`, `spectatorCanStillTakeASeatAndPlayersCannotSpectate`

---

### UC24. Thôi xem trận

| | |
|---|---|
| **Actor** | Người xem |
| **Mô tả** | Rời phòng đang xem, chủ động hoặc vì phòng bị đóng |
| **Tiền điều kiện** | Đang xem một phòng |
| **Hậu điều kiện** | Không còn trong danh sách người xem, không nhận sự kiện của trận nữa, trạng thái online về `IDLE` |
| **Thông điệp** | `LEAVE_ROOM`, push `SPECTATE_ENDED {roomId}` |

**Luồng chính (chủ động)**
1. Bấm Thôi xem. `LobbyService.leaveRoom()`: không có phòng trong `roomByUser` nên gọi `stopSpectating()`:
   xóa khỏi `spectating`, `room.removeSpectator()`, `match.removeSpectator()`, gửi `ROOM_UPDATE`.
2. `broadcastLobby()`. Trả `{}`.

**Luồng thay thế (tự rời)**
- Phòng bị xóa (hết người chơi): `deleteRoom()` gửi `SPECTATE_ENDED` cho từng người xem. Client báo "Phòng đã đóng" và về sảnh.
- Tự thôi xem khi: tạo phòng, vào phòng, được ghép trận, chấp nhận lời mời, chơi với máy (`openRoom`/`seat` gọi `stopSpectating`),
  hoặc khi mất kết nối.

**Mã nguồn**
- Server: `LobbyService.leaveRoom()`, `stopSpectating()`, `deleteRoom()`
- Desktop: `GameClient.leaveRoom()`, `handleEvent` case `SPECTATE_ENDED`. Web: `store.leaveRoom()`, `store.ts` case `SPECTATE_ENDED`
- Test: `RoomExtrasIntegrationTest.spectatorIsSentBackToTheLobbyWhenTheRoomCloses`

---

### UC25. Trò chuyện trong phòng

| | |
|---|---|
| **Actor** | Người chơi, Người xem |
| **Mô tả** | Gửi tin nhắn cho mọi người trong cùng phòng |
| **Tiền điều kiện** | Đang ở trong phòng (ngồi chơi hoặc đang xem). Chưa vượt giới hạn chat |
| **Hậu điều kiện** | Mọi người trong phòng lúc đó, kể cả người gửi, nhận tin. Tin **không** lưu DB, người vào sau không thấy tin cũ |
| **Thông điệp** | `CHAT {text}`, push `CHAT_MESSAGE {roomId, from, spectator, text, sentAt}` |

**Luồng chính**
1. Gõ tin và bấm Gửi.
2. `MessageDispatcher` lấy một token ở **xô chat** riêng (5 tin liên tiếp, sau đó 1 tin mỗi 2 giây), ngoài xô chung.
3. `LobbyService.chat()`:
   1. thay ký tự điều khiển bằng dấu cách, bỏ khoảng trắng hai đầu, độ dài phải từ 1 đến 200 ký tự;
   2. tìm phòng mình đang ngồi chơi, nếu không có thì phòng đang xem;
   3. gửi `CHAT_MESSAGE` cho `room.memberIds()`, gồm người chơi và người xem. Trường `spectator` cho biết người gửi có phải người xem.
4. Client hiển thị tin dưới dạng **văn bản thuần**, để ai gửi thẻ HTML cũng không chèn được giao diện:
   - desktop: khung chat là `JTextArea`, không bao giờ diễn giải HTML. Các nhãn có tên người chơi dùng `Ui.html()`, hàm này escape trước khi bọc `<html>`;
   - web: React tự escape chuỗi khi render.

**Luồng ngoại lệ:** `VALIDATION_ERROR` (rỗng hoặc dài quá 200), `NOT_IN_ROOM`, `RATE_LIMITED`.

**Mã nguồn**
- Server: `MessageDispatcher` case `CHAT`, `LobbyService.chat()`, `Room.memberIds()`
- Desktop: `GameClient.sendChat()`, `ui/RoomPanel`. Web: `store.sendChat()`, `Room.tsx` (`Chat`)
- Test: `RoomExtrasIntegrationTest.spectatorWatchesTheMatchAndEveryoneCanChat`, `RateLimitIntegrationTest.chatHasItsOwnStricterLimit`

---

## 5. Hồ sơ, lịch sử và xếp hạng

Bốn use case này chỉ đọc DB. Cùng một dữ liệu có hai đường vào: **socket** (`GET_*`, desktop dùng) và **REST** (web dùng).
Cả hai gọi chung `PlayerQueryService` nên trả về **đúng cùng JSON**.

### UC26. Xem hồ sơ cá nhân / đối thủ

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Xem tên hiển thị, Elo, thứ hạng, online hay không, ngày tham gia, thống kê thắng/thua/hòa |
| **Tiền điều kiện** | Đã đăng nhập |
| **Hậu điều kiện** | Không đổi trạng thái |
| **Thông điệp / API** | `GET_PROFILE {userId?}` (bỏ `userId` thì xem chính mình) · `GET /api/users/{id}` |

**Luồng chính**
1. Bấm vào tên mình hoặc tên người khác (danh sách online, thẻ người chơi trong phòng, bảng xếp hạng).
2. `PlayerQueryService.profile(userId)`:
   1. `requireUser()`;
   2. `rankOf()`: đếm số người chơi `ACTIVE` có rating **cao hơn**, cộng 1. Bằng điểm thì cùng hạng.
      Admin hoặc tài khoản bị khóa thì `rank = null`;
   3. `stats()`: một câu truy vấn gộp `matches.statsOf(userId)` đếm số trận, thắng, hòa (không tính `ABORTED`), suy ra thua và tỉ lệ thắng;
   4. `online` lấy từ `SessionRegistry.isOnline()`.
3. Trả `Profile`.

**Luồng ngoại lệ:** `USER_NOT_FOUND`.

**Mã nguồn**
- Server: `PlayerQueryService.profile()`, `rankOf()`, `stats()`, `MatchRecordRepository.statsOf()`, `PlayerController`
- Desktop: `GameClient.profile()`, `ui/ProfileDialog`. Web: `rest.profile`, `Modals.tsx` (`ProfileModal`)
- Test: `RestApiIntegrationTest.historyProfileReplayAndLeaderboardAfterAMatch`

---

### UC27. Xem lịch sử đấu

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Danh sách trận đã đấu (phân trang, mới nhất trước), nhìn từ phía người được hỏi |
| **Tiền điều kiện** | Đã đăng nhập |
| **Hậu điều kiện** | Không đổi trạng thái |
| **Thông điệp / API** | `GET_HISTORY {userId?, page?, size?}` · `GET /api/users/{id}/matches?page&size` → `Page<HistoryItem>` |

**Luồng chính**
1. Mở Lịch sử. `PlayerQueryService.history(userId, page, size)`:
   1. `matches.findByPlayer()` lấy các trận người đó cầm X hoặc O, sắp theo `ended_at` giảm dần;
   2. `playersOf()` nạp thông tin mọi đối thủ trong trang bằng **một** truy vấn, tránh lỗi N+1;
   3. `HistoryItem.of()` chuyển mỗi trận sang góc nhìn của người được hỏi: quân cầm, `outcome` (`WIN`/`LOSS`/`DRAW`/`ABORTED`),
      điểm người đó nhận được.
2. Trả `Page {items, page, size, totalItems, totalPages}`. `page` bắt đầu từ 0, `size` từ 1 đến 100.
3. Bấm vào một dòng để phát lại trận đó (UC28).

**Lưu ý:** trận với bot không có trong lịch sử. `MATCH_END` tới trước khi trận kịp ghi DB vài mili giây, nên client không nên gọi
lịch sử ngay khi vừa nhận `MATCH_END`.

**Mã nguồn**
- Server: `PlayerQueryService.history()`, `playersOf()`, `HistoryItem`, `MatchRecordRepository.findByPlayer()`
- Desktop: `GameClient.history()`, `ui/HistoryDialog`. Web: `rest.history`, `Modals.tsx` (`HistoryModal`, `Pager`)
- Test: `RestApiIntegrationTest.historyProfileReplayAndLeaderboardAfterAMatch`

---

### UC28. Phát lại ván đấu

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Xem lại từng nước của một ván đã kết thúc, kèm đường 5 quân thắng |
| **Tiền điều kiện** | Trận đã kết thúc và đã được lưu |
| **Hậu điều kiện** | Không đổi trạng thái |
| **Thông điệp / API** | `GET_MATCH {matchId}` · `GET /api/matches/{matchId}` → `MatchDetail {match, winLine, moves}` |

**Luồng chính**
1. Chọn một trận trong lịch sử (hoặc trong trang admin). `PlayerQueryService.match(matchId)`:
   1. đọc `MatchRecord` cùng danh sách `match_moves` theo thứ tự `move_no`;
   2. suy ra quân X/O của từng nước;
   3. `winLine()`: `winLine` **không được lưu trong DB**. Server dựng lại bàn cờ từ các nước đi rồi gọi lại
      `RuleEngine.findWinLine()` cho nước cuối, có xét luật chặn hai đầu đã lưu cùng trận.
2. Client có các nút Đầu / Lùi / Tới / Cuối (hoặc tự chạy), mỗi bước vẽ bàn tới nước thứ n, đến nước cuối thì tô đường thắng.

**Luồng ngoại lệ:** `MATCH_NOT_FOUND` (không có trận đã lưu nào có id đó). `BAD_REQUEST` khi `matchId` không phải UUID.

**Mã nguồn**
- Server: `PlayerQueryService.match()`, `winLine()`, `MatchDetail`, `MatchRecord`, `MoveRecord`
- Desktop: `GameClient.matchDetail()`, `ui/ReplayDialog`. Web: `rest.match`, `Modals.tsx` (`ReplayModal`)
- Test: `RestApiIntegrationTest.historyProfileReplayAndLeaderboardAfterAMatch`

**Hay bị hỏi:** *Vì sao không lưu `winLine`?* Đường thắng tính lại được từ các nước đi, nên lưu thêm là thừa và có nguy cơ lệch
với dữ liệu nước đi. Luật chặn hai đầu được lưu (cột `block_two_ends`, migration V2) để tính lại cho đúng.

---

### UC29. Xem bảng xếp hạng

| | |
|---|---|
| **Actor** | Người chơi |
| **Mô tả** | Bảng xếp hạng toàn hệ thống theo Elo giảm dần, phân trang |
| **Tiền điều kiện** | Đã đăng nhập |
| **Hậu điều kiện** | Không đổi trạng thái |
| **Thông điệp / API** | `GET_LEADERBOARD {page?, size?}` · `GET /api/leaderboard?page&size` → `Page<LeaderboardEntry>` |

**Luồng chính**
1. Mở Bảng xếp hạng. `PlayerQueryService.leaderboard()`:
   1. lấy người chơi role `PLAYER`, status `ACTIVE`, sắp theo rating giảm dần;
   2. tính hạng kiểu **1, 2, 2, 4**: bằng điểm thì cùng hạng, người kế tiếp nhảy hạng.
      Dòng đầu mỗi trang lấy hạng bằng `rankOf()`, vì trang có thể bắt đầu giữa một nhóm cùng điểm;
   3. kèm cờ `online`.
2. Trả `Page`. Bấm vào tên để xem hồ sơ (UC26).

**Mã nguồn**
- Server: `PlayerQueryService.leaderboard()`, `rankOf()`, `UserRepository.findByRoleAndStatus()`
- Desktop: `GameClient.leaderboard()`, `ui/LeaderboardDialog`. Web: `rest.leaderboard`, `Modals.tsx` (`LeaderboardModal`)
- Test: `RestApiIntegrationTest.historyProfileReplayAndLeaderboardAfterAMatch`

---

## 6. Quản trị (trên web)

Mọi API `/api/admin/**` yêu cầu JWT của tài khoản role `ADMIN` (`SecurityConfig`: `.requestMatchers("/api/admin/**").hasRole("ADMIN")`).
Người khác nhận `403 FORBIDDEN`, chưa đăng nhập nhận `401`. `JwtAuthFilter` đọc lại user trong DB ở **mỗi request**,
nên admin vừa bị đổi quyền hay bị khóa thì token cũ cũng hết tác dụng. Tài khoản admin mặc định do `AdminSeeder` tạo
(`admin`/`admin123`, đổi được bằng biến môi trường). Giao diện: tab Quản trị, [AdminPage.tsx](../client/web/src/admin/AdminPage.tsx).

### UC30. Xem thống kê hệ thống

| | |
|---|---|
| **Actor** | Admin |
| **Mô tả** | Xem các con số tổng quan của hệ thống |
| **Tiền điều kiện** | Đăng nhập bằng tài khoản ADMIN |
| **Hậu điều kiện** | Không đổi trạng thái |
| **API** | `GET /api/admin/stats` → `SystemStats` |

**Luồng chính**
1. Mở tab Quản trị. `AdminService.stats()` gom số liệu từ hai nguồn:
   - **DB**: tổng người dùng, số tài khoản bị khóa, tổng số trận, số trận kết thúc trong 24 giờ qua;
   - **RAM**: số người online (`SessionRegistry.onlineCount()`), số phòng (`LobbyService.roomCount()`),
     số trận đang diễn ra (`activeMatchCount()`, đếm phòng `PLAYING`).
2. Web hiện các ô thống kê. Sau khi khóa/mở khóa thì số liệu được tải lại.

**Mã nguồn:** `AdminController`, `AdminService.stats()`, `SystemStats` · Web: `AdminPage.tsx`, `rest.admin.stats`

---

### UC31. Quản lý danh sách người dùng

| | |
|---|---|
| **Actor** | Admin |
| **Mô tả** | Tìm người dùng theo username hoặc tên hiển thị, xem role, trạng thái, rating, online |
| **Tiền điều kiện** | Là ADMIN |
| **Hậu điều kiện** | Không đổi trạng thái |
| **API** | `GET /api/admin/users?q&page&size` → `Page<AdminUserView>` |

**Luồng chính**
1. Gõ từ khóa vào ô tìm kiếm. `AdminService.listUsers(q, page, size)`:
   - `q` trống: `users.findAll()`;
   - `q` có giá trị: `users.search()` tìm theo một phần username hoặc tên hiển thị.
   Sắp theo người tạo trước.
2. Mỗi dòng là `AdminUserView` kèm cờ `online`. Từ đây bấm Khóa / Mở khóa (UC32, UC33).

**Mã nguồn:** `AdminService.listUsers()`, `UserRepository.search()`, `AdminUserView` · Web: `AdminPage.tsx` (`Users`)

---

### UC32. Khóa tài khoản

| | |
|---|---|
| **Actor** | Admin |
| **Mô tả** | Khóa tài khoản vi phạm. Nếu người đó đang online thì bị đá ra ngay |
| **Tiền điều kiện** | Là ADMIN. Tài khoản cần khóa không phải ADMIN |
| **Hậu điều kiện** | `users.status = LOCKED`. Người đó không đăng nhập được nữa và token cũ cũng bị từ chối. Nếu đang online: nhận `KICKED {ACCOUNT_LOCKED}`, kết nối đóng; nếu đang trong trận thì sau 30 giây sẽ thua `DISCONNECT` vì không thể đăng nhập lại |
| **API** | `POST /api/admin/users/{id}/lock` → `AdminUserView` |

**Luồng chính**
1. Bấm Khóa ở một dòng (web hỏi xác nhận). `AdminService.lock(userId)`:
   1. `requireUser()`; tài khoản là ADMIN thì từ chối;
   2. `users.updateStatus(userId, LOCKED)`: **chỉ cập nhật cột status**, không ghi đè cả entity,
      để không đè mất rating nếu trận của người đó vừa kết thúc cùng lúc;
   3. **ghi DB trước, đá sau**: `sessions.kick(userId, "ACCOUNT_LOCKED")`. Nếu đá trước thì client có thể kịp đăng nhập lại
      trước khi status được ghi.
2. Kết nối bị đóng và được xử lý như mất kết nối (UC21): rời hàng chờ, đóng lời mời, trận tạm dừng.
   Client cố kết nối lại bằng `AUTH` nhưng nhận `ACCOUNT_LOCKED`, nên trận kết thúc khi hết 30 giây.
3. Client bị khóa hiện thông báo "Tài khoản đã bị khóa" và về màn hình đăng nhập.

**Luồng ngoại lệ:** `USER_NOT_FOUND`. `FORBIDDEN` khi cố khóa admin.

**Mã nguồn**
- Server: `AdminService.lock()`, `UserRepository.updateStatus()`, `SessionRegistry.kick()`, `AuthService.requireActive()`
- Web: `AdminPage.tsx` (`Users`), `rest.admin.lock`. Desktop/web phía bị khóa: case `KICKED`
- Test: `RestApiIntegrationTest.adminLocksAnOnlinePlayerAndUnlocksThem`

---

### UC33. Mở khóa tài khoản

| | |
|---|---|
| **Actor** | Admin |
| **Mô tả** | Khôi phục tài khoản đã bị khóa |
| **Tiền điều kiện** | Là ADMIN |
| **Hậu điều kiện** | `users.status = ACTIVE`. Người đó đăng nhập lại được và xuất hiện lại trên bảng xếp hạng |
| **API** | `POST /api/admin/users/{id}/unlock` → `AdminUserView` |

**Luồng chính:** bấm Mở khóa. `AdminService.unlock()`: `requireUser()`, `users.updateStatus(userId, ACTIVE)`, trả view mới.

**Luồng ngoại lệ:** `USER_NOT_FOUND`.

**Mã nguồn:** `AdminService.unlock()` · Web: `AdminPage.tsx` · Test: `RestApiIntegrationTest.adminLocksAnOnlinePlayerAndUnlocksThem`

---

### UC34. Giám sát phòng và trận đấu

| | |
|---|---|
| **Actor** | Admin |
| **Mô tả** | Xem mọi phòng đang mở (kể cả người xem, bot, luật) và lịch sử mọi trận đã lưu |
| **Tiền điều kiện** | Là ADMIN |
| **Hậu điều kiện** | Không đổi trạng thái |
| **API** | `GET /api/admin/rooms` → `[RoomInfo]` · `GET /api/admin/matches?page&size` → `Page<MatchSummary>` |

**Luồng chính**
1. Tab Phòng: gọi `LobbyService.listRooms()`, dữ liệu lấy từ RAM.
2. Tab Trận: gọi `PlayerQueryService.recentMatches()`, dữ liệu lấy từ DB, mới nhất trước. `MatchSummary` mang góc nhìn trung lập
   (không theo phía người chơi nào). Bấm vào một trận để phát lại (UC28).

**Mã nguồn:** `AdminController`, `LobbyService.listRooms()`, `PlayerQueryService.recentMatches()`, `MatchSummary` ·
Web: `AdminPage.tsx` (`Rooms`, `Matches`)

---

## 7. Dịch vụ nền hệ thống

### UC35. Kiểm tra nhịp tim (heartbeat)

| | |
|---|---|
| **Actor** | Hệ thống |
| **Mô tả** | Client gửi `PING` mỗi 10 giây. Server không nhận được thông điệp nào trong 30 giây thì coi là mất kết nối |
| **Tiền điều kiện** | Có kết nối TCP/WebSocket |
| **Hậu điều kiện** | Kết nối còn sống được giữ. Kết nối chết bị đóng và chuyển sang xử lý mất kết nối (UC21) |
| **Thông điệp** | `PING` → `PONG {serverTime}` (cùng `reqId`) |

**Luồng chính**
1. Client hẹn giờ gửi `PING` mỗi 10 giây:
   - desktop: `Connection.ping()` trên một scheduler riêng;
   - web: `setInterval` trong `GameSocket`.
2. Server: **mọi** thông điệp tới đều gọi `session.touch()`, cập nhật `lastSeenAt`. `PING` được trả lời `PONG` kèm giờ server,
   và không bị rate limit.
3. Client dùng `serverTime` để tính độ lệch đồng hồ, phục vụ đếm ngược chính xác (UC15).
4. `HeartbeatMonitor.sweep()` chạy mỗi 5 giây. Phiên nào có `now - lastSeenAt > 30 giây` thì bị `session.close()` rồi `dispatcher.onClose(session)`.

**Hay bị hỏi**
- *Vì sao cần heartbeat khi TCP đã có cơ chế báo đóng?* Khi rút dây mạng hoặc máy client treo, TCP không gửi gói FIN.
  Server có thể không biết kết nối đã chết trong rất lâu, vì TCP keepalive mặc định khoảng 2 giờ. Heartbeat ở tầng ứng dụng
  phát hiện được trong khoảng 30–35 giây.
- *Vì sao `PING` không bị rate limit?* Nếu client bị giới hạn vì spam mà heartbeat cũng bị chặn, client sẽ bị coi là mất kết nối oan.

**Mã nguồn**
- Server: `HeartbeatMonitor`, `ClientSession.touch()`/`lastSeenAt()`, `MessageDispatcher` case `PING`
- Desktop: `Connection.ping()`, `serverNow()`. Web: `GameSocket` (`ping`, `serverNow`)
- Test: `RateLimitIntegrationTest.tooManyMessagesAreRejectedButPingAlwaysWorks`

---

### UC36. Giới hạn tốc độ (rate limit)

| | |
|---|---|
| **Actor** | Hệ thống |
| **Mô tả** | Chống spam theo từng kết nối, và chống dò mật khẩu theo từng IP |
| **Tiền điều kiện** | Có thông điệp hoặc request tới |
| **Hậu điều kiện** | Trong giới hạn: xử lý bình thường. Vượt giới hạn: thông điệp **bị bỏ qua**, client nhận `RATE_LIMITED` (REST trả HTTP 429), kết nối **không** bị đóng |
| **Thông điệp** | `ERROR {code: RATE_LIMITED}` |

**Ba xô token**

| Xô | Áp dụng | Tối đa liên tiếp | Nạp lại | Ở đâu |
|---|---|---|---|---|
| Chung | Mọi thông điệp trừ `PING`, kể cả thông điệp hỏng | 20 | 10/giây | `ClientSession.messageLimiter()`, kiểm tra trong `onMessage()` |
| Chat | `CHAT` (tính thêm ngoài xô chung) | 5 | 0,5/giây (1 tin mỗi 2 giây) | `ClientSession.chatLimiter()`, kiểm tra trong case `CHAT` |
| Đăng nhập/đăng ký | Theo IP: `LOGIN`, `REGISTER`, `POST /api/auth/login`, `/register` | 20 | khoảng 1 lần mỗi 3 giây | `LoginThrottle` (map IP → `RateLimiter`), `LoginThrottleFilter` cho REST |

**Luồng chính (token bucket)**
1. Mỗi xô có tối đa `burst` token, ban đầu đầy.
2. `RateLimiter.tryAcquire()` nạp thêm token theo thời gian đã trôi qua (`(now - lastRefill) × perSecond`, không vượt `burst`).
   Còn ít nhất 1 token thì lấy 1 và cho qua, hết token thì từ chối.
3. Xô theo IP không dùng tới 10 phút thì bị dọn (`LoginThrottle.startCleanup`), để map không phình mãi.
4. IP thật của client: khi đi qua nginx, server đọc header `X-Real-IP`, nhưng **chỉ tin** header này nếu request tới từ
   địa chỉ loopback hoặc mạng nội bộ, tức là chính proxy (`ClientIp`). Client ngoài tự gửi header giả thì bị bỏ qua.

**Hay bị hỏi**
- *Vì sao giới hạn đăng nhập theo IP chứ không theo kết nối?* Kẻ dò mật khẩu chỉ cần mở kết nối mới là có xô mới.
  Theo IP thì mở thêm kết nối cũng không vượt được giới hạn.
- *Hạn chế?* Chạy bằng Docker Desktop thì mọi kết nối từ ngoài bị NAT thành cùng một IP, nên mọi người dùng chung một xô đăng nhập.
  Mức 20 lần liên tiếp vẫn đủ cho một lớp đăng nhập cùng lúc.

**Mã nguồn**
- Server: `RateLimiter`, `LoginThrottle`, `LoginThrottleFilter`, `ClientIp`, `MessageDispatcher.onOpen()`/`onMessage()`, `application.yml` (`caro.rate-limit.*`)
- Test: `RateLimiterTest`, `RateLimitIntegrationTest.tooManyMessagesAreRejectedButPingAlwaysWorks`, `chatHasItsOwnStricterLimit`,
  `loginAttemptsAreLimitedPerAddress`, `ClientIpTest`

---

### UC37. Tính Elo và lưu ván cờ vào CSDL

| | |
|---|---|
| **Actor** | Hệ thống |
| **Mô tả** | Khi một trận có tính điểm kết thúc: tính Elo, rồi trong **một transaction** ghi kết quả, toàn bộ nước đi và cộng điểm |
| **Tiền điều kiện** | Trận vừa `finish()` và `options.rated() == true` (không phải trận bot) |
| **Hậu điều kiện** | Một dòng trong `matches`, n dòng trong `match_moves`, rating của hai người được cộng thêm. Trận `ABORTED` được lưu nhưng không đổi điểm |
| **Thông điệp** | Nội bộ. Điểm thay đổi có trong `MATCH_END.ratingChange` |

**Luồng chính**
1. **Tính Elo** trong `GameMatch.finish()` bằng `Elo.deltaForX(ratingX, ratingO, scoreX)`:
   - `E_x = 1 / (1 + 10^((R_o − R_x) / 400))`;
   - `S_x` là 1 nếu X thắng, 0,5 nếu hòa, 0 nếu X thua;
   - `Δx = round(32 × (S_x − E_x))`, `Δo = −Δx`, nên tổng điểm hai bên không đổi;
   - `R_x`, `R_o` là rating **đọc lại từ DB lúc trận bắt đầu** (`startMatch` → `loadPlayer`), không phải rating lúc kết thúc;
   - trận `ABORTED` hoặc không tính điểm: Δ = 0.
2. `MATCH_END` được gửi ngay, kèm `ratingChange`.
3. Sau khi nhả khóa trận, `LobbyService.onMatchEnded()` gọi `MatchPersistenceService.save(match)` (`@Transactional`):
   1. `MatchRecord.of(match)` tạo bản ghi gồm các nước đi;
   2. `em.persist(record)`: dùng **persist chứ không merge**, vì id là `matchId` do server tự đặt. Nếu lỡ lưu lần thứ hai,
      câu INSERT trùng khóa chính sẽ lỗi thay vì âm thầm ghi đè;
   3. nếu không phải `ABORTED`: `users.addRating(xId, Δx)` và `users.addRating(oId, Δo)`,
      tức là câu lệnh `UPDATE users SET rating = rating + :delta`, cộng nguyên tử.
   4. Bất kỳ bước nào lỗi thì transaction rollback toàn bộ: hoặc lưu đủ, hoặc không lưu gì.
4. Cập nhật rating mới trong danh sách online và trong phòng, broadcast lại.

**Schema** (Flyway quản lý, Hibernate chỉ `validate`):
- `V1__init.sql`: bảng `users`, `matches` (khóa chính UUID), `match_moves` (khóa chính `(match_id, move_no)`);
- `V2__match_rules.sql`: thêm cột `block_two_ends`;
- `V3__agreed_draw.sql`: cho phép lý do `AGREED`.

**Hay bị hỏi**
- *Vì sao cộng điểm bằng `rating = rating + delta` chứ không đọc entity, sửa rồi lưu?* Một người có thể có hai trận kết thúc gần
  như cùng lúc, hoặc admin khóa tài khoản đúng lúc trận kết thúc. Đọc, sửa rồi ghi cả entity sẽ làm mất cập nhật của bên kia
  (lost update). Câu lệnh cộng nguyên tử trong DB thì không bị.
- *Vì sao tính Elo theo rating lúc bắt đầu?* Trong lúc trận diễn ra, rating của người chơi có thể đã thay đổi vì một trận khác
  vừa được lưu. Tính theo rating lúc bắt đầu thì kết quả nhất quán với những gì hai người thấy khi vào trận.
- *Vì sao trận bot không lưu?* Bot không có dòng trong bảng `users` (id `-1`), và trận với máy không được tính vào xếp hạng.

**Mã nguồn**
- Server: `Elo`, `GameMatch.finish()`, `LobbyService.onMatchEnded()`, `MatchPersistenceService.save()`, `MatchRecord`, `MoveRecord`,
  `UserRepository.addRating()`, `db/migration/V1..V3`
- Test: `EloTest`, `GameFlowIntegrationTest.tcpPlayerBeatsWebPlayerAndTheResultIsStored`, `GameMatchTest.bothDisconnectedAbortsWithoutRatingChange`,
  `unratedMatchHasAWinnerButNoRatingChange`

---

## Phụ lục: luồng đầu-cuối của một nước đi (ai cũng phải nắm)

```
[Desktop] bấm ô (7,7)
  BoardPanel → GameClient.move(7,7) → Connection.request("MOVE", ...)
  → ghi dòng {"type":"MOVE","reqId":"c-12","data":{"matchId":"…","x":7,"y":7}}\n vào socket TCP
[Server]
  TcpServer.serve() (virtual thread của kết nối) đọc dòng
  → MessageDispatcher.onMessage(): touch, parse, rate limit, kiểm tra đăng nhập
  → handle() case MOVE → LobbyService.move()
       requireMatch() trong khóa lobby, sau đó nhả khóa lobby
  → GameMatch.move() trong khóa trận: kiểm tra → board.place → RuleEngine.findWinLine → đổi lượt → restartTurnTimer
       → broadcast MOVE_APPLIED → SessionRegistry.sendToUser() → ClientSession.send() (vào hàng đợi, không block)
  → trả OK {moveNo} cho người đi
  (nếu thắng: finish() → MATCH_END; sau khi nhả khóa trận → onMatchEnded() → MatchPersistenceService.save())
[Writer thread của từng phiên] lấy từ hàng đợi, ghi ra TCP (desktop) hoặc WebSocket (web)
[Web của đối thủ] GameSocket nhận frame → store.ts case MOVE_APPLIED → state/match.ts → React vẽ quân
[Desktop của người đi] thread đọc của Connection → EDT → GameClient.handleEvent → BoardPanel vẽ quân
```
