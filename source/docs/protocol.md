# GIAO THỨC GAME (TCP + WEBSOCKET)

> Đây là "hợp đồng" giữa server và hai loại client. Client desktop (Socket) và client Web dùng
> **cùng một định dạng thông điệp**; chỉ khác tầng truyền tải bên dưới.
> Muốn thêm hay sửa thông điệp thì cập nhật file này trước.

## 1. Tầng truyền tải

| Client | Kết nối | Đóng khung thông điệp (framing) |
|---|---|---|
| Desktop (Socket) | TCP thuần, `host:9090` | Mỗi thông điệp là **một dòng JSON UTF-8**, kết thúc bằng `\n`. Tối đa 8192 ký tự mỗi dòng |
| Web | WebSocket, `ws://host:8080/ws` | Mỗi WebSocket text frame chứa đúng **một** thông điệp JSON |

TCP là một dòng byte liên tục (stream), không có khái niệm "gói tin". Vì vậy cần ký tự `\n` để
đánh dấu ranh giới giữa các thông điệp. Nhờ cách này có thể thử giao thức bằng `telnet`/`ncat` (xem mục 7).

Ngoài giao thức game, server còn có REST API (dùng chủ yếu cho Web):

| Endpoint | Method | Body | Trả về |
|---|---|---|---|
| `/api/auth/register` | POST | `{"username","password","displayName"?}` | `201` + `UserInfo` |
| `/api/auth/login` | POST | `{"username","password"}` | `{"token","user": UserInfo}` |
| `/api/auth/me` | GET | Header `Authorization: Bearer <token>` | `UserInfo` |
| `/api/health` | GET | (không có) | `{"status":"ok","onlineUsers","rooms"}` |

Lỗi REST có dạng: `{"code": "<ERROR_CODE>", "message": "..."}`.

## 2. Phong bì thông điệp (envelope)

Mọi thông điệp, theo cả hai chiều, đều có dạng:

```json
{"type": "MOVE", "reqId": "c-17", "data": { ... }}
```

| Trường | Ý nghĩa |
|---|---|
| `type` | Loại thông điệp (bắt buộc) |
| `reqId` | Do client tự đặt khi gửi **yêu cầu**. Server trả lời với **cùng `reqId`** để client ghép cặp yêu cầu và phản hồi. Sự kiện server chủ động đẩy (push) không có `reqId` |
| `data` | Nội dung, có thể là `{}` hoặc bỏ trống |

Mỗi yêu cầu nhận về đúng một phản hồi:
- Thành công: `{"type":"OK","reqId":"c-17","data":{...}}`
- Thất bại: `{"type":"ERROR","reqId":"c-17","data":{"code":"NOT_YOUR_TURN","message":"..."}}`

Riêng `PING` được trả lời bằng `PONG` (cũng mang `reqId` của yêu cầu).

## 3. Client → Server

Chỉ `PING`, `REGISTER`, `LOGIN`, `AUTH` được dùng khi chưa đăng nhập. Gửi bất kỳ thông điệp nào khác
trước khi đăng nhập sẽ nhận lỗi `UNAUTHORIZED`.

| type | data gửi lên | data trong `OK` | Ghi chú |
|---|---|---|---|
| `PING` | (không có) | `PONG`: `{"serverTime": <epoch ms>}` | Gửi mỗi 10 giây (heartbeat) |
| `REGISTER` | `{"username","password","displayName"?}` | `{"user": UserInfo}` | Chỉ tạo tài khoản, chưa đăng nhập |
| `LOGIN` | `{"username","password"}` | `{"token","user": UserInfo}` | Dùng cho client desktop |
| `AUTH` | `{"token"}` | `{"user": UserInfo}` | Dùng cho Web: lấy token qua REST rồi gửi làm thông điệp đầu tiên trên WS |
| `LOGOUT` | (không có) | `{}` | Tự rời phòng (đang trong trận thì tính là đầu hàng), sau đó server đóng kết nối |
| `GET_STATE` | (không có) | `{"room": RoomInfo\|null, "match": MatchState\|null}` | Đồng bộ lại toàn bộ trạng thái |
| `LIST_ROOMS` | (không có) | `{"rooms": [RoomInfo]}` | |
| `CREATE_ROOM` | `{"name"?}` | `{"room": RoomInfo}` | Tên tối đa 40 ký tự. Bỏ trống thì server tự đặt |
| `JOIN_ROOM` | `{"roomId"}` | `{"room": RoomInfo}` | Trận bắt đầu ngay khi phòng đủ 2 người |
| `LEAVE_ROOM` | (không có) | `{}` | Rời phòng khi trận đang diễn ra tính là đầu hàng |
| `MOVE` | `{"matchId","x","y"}` | `{"moveNo"}` | Kết quả của nước đi được gửi qua sự kiện `MOVE_APPLIED` |
| `RESIGN` | `{"matchId"}` | `{}` | |

Sau khi `LOGIN`/`AUTH` thành công, nếu người dùng đang ở trong một phòng hoặc trận (trường hợp kết nối lại),
server tự gửi thêm `ROOM_UPDATE` và `MATCH_STATE`.

## 4. Server → Client (sự kiện push)

| type | Gửi cho ai | data |
|---|---|---|
| `ROOM_LIST` | Mọi người đã đăng nhập, mỗi khi danh sách phòng thay đổi | `{"rooms": [RoomInfo]}` |
| `ROOM_UPDATE` | Các thành viên trong phòng | `{"room": RoomInfo}` |
| `MATCH_START` | Hai người chơi | `MatchState` |
| `MOVE_APPLIED` | Hai người chơi | `{"matchId","moveNo","x","y","mark","nextTurn","turnDeadline"}` |
| `MATCH_END` | Hai người chơi | `MatchResult` |
| `MATCH_STATE` | Người vừa kết nối lại | `MatchState` |
| `OPPONENT_DISCONNECTED` | Người còn lại | `{"matchId","userId","graceDeadline"}` |
| `OPPONENT_RECONNECTED` | Người còn lại | `{"matchId","userId","turnDeadline"}` |
| `KICKED` | Phiên bị đá | `{"reason": "LOGGED_IN_ELSEWHERE" \| "ACCOUNT_LOCKED"}`. Server đóng kết nối ngay sau đó |
| `ERROR` (không có `reqId`) | Phiên gửi dữ liệu hỏng | `{"code","message"}`. Ví dụ JSON sai cú pháp |

Mọi mốc thời gian (`turnDeadline`, `graceDeadline`, `serverTime`) đều tính bằng **epoch milliseconds
theo đồng hồ server**. Client nên dùng `serverTime` trong `PONG` để bù độ lệch đồng hồ khi hiển thị đếm ngược.

## 5. Các kiểu dữ liệu

```jsonc
// UserInfo: thông tin của chính mình
{"id": 1, "username": "alice", "displayName": "Alice", "role": "PLAYER", "rating": 1000}

// PlayerInfo: thông tin công khai của người chơi khác
{"id": 2, "username": "bob", "displayName": "Bob", "rating": 1016}

// RoomInfo
{
  "id": "R1",
  "name": "Phòng của alice",
  "status": "WAITING",          // WAITING | PLAYING | FINISHED
  "hostId": 1,
  "players": [PlayerInfo],      // 1 hoặc 2 người, host đứng đầu
  "matchId": null               // id của trận gần nhất trong phòng (nếu có)
}

// MatchState: ảnh chụp đầy đủ của một trận (riêng cho từng người nhận)
{
  "matchId": "6f1c...",
  "roomId": "R1",
  "boardSize": 15,
  "playerX": PlayerInfo,
  "playerO": PlayerInfo,
  "yourMark": "X",               // X | O
  "status": "IN_PROGRESS",       // IN_PROGRESS | PAUSED | FINISHED
  "turn": "O",
  "moveCount": 3,
  "board": ["...............", "....X..........", ...],  // 15 chuỗi, mỗi chuỗi là một hàng y; ký tự thứ x là '.', 'X' hoặc 'O'
  "lastMove": {"x": 4, "y": 1, "mark": "X"},             // null nếu chưa có nước nào
  "turnDeadline": 1761200000000, // null khi PAUSED hoặc FINISHED
  "turnTimeLeftMs": 30000,       // thời gian còn lại của lượt hiện tại
  "result": null                 // MatchResult khi status = FINISHED
}

// MatchResult
{
  "matchId": "6f1c...",
  "result": "X_WIN",             // X_WIN | O_WIN | DRAW | ABORTED
  "reason": "FIVE_IN_ROW",       // FIVE_IN_ROW | BOARD_FULL | RESIGN | TIMEOUT | DISCONNECT | BOTH_DISCONNECTED
  "winnerId": 1,                 // null nếu hòa hoặc bị hủy
  "winLine": [[3,1],[4,1],[5,1],[6,1],[7,1]],  // chỉ có khi reason = FIVE_IN_ROW
  "ratingChange": {"X": 16, "O": -16}
}
```

## 6. Mã lỗi

| Mã | Khi nào |
|---|---|
| `BAD_REQUEST` | JSON sai cú pháp, thiếu `type`, sai kiểu dữ liệu |
| `UNKNOWN_TYPE` | `type` không được hỗ trợ |
| `UNAUTHORIZED` | Chưa đăng nhập, hoặc token sai/hết hạn |
| `ALREADY_AUTHENTICATED` | Gửi `LOGIN`/`AUTH` khi phiên đã đăng nhập |
| `VALIDATION_ERROR` | Dữ liệu không hợp lệ (username, password, tên phòng...) |
| `USERNAME_TAKEN` | Username đã tồn tại |
| `INVALID_CREDENTIALS` | Sai username hoặc mật khẩu |
| `ACCOUNT_LOCKED` | Tài khoản bị khóa |
| `ALREADY_IN_ROOM` | Tạo hoặc vào phòng khi đang ở một phòng khác |
| `NOT_IN_ROOM` | Rời phòng khi không ở phòng nào |
| `ROOM_NOT_FOUND` | Phòng không tồn tại |
| `ROOM_FULL` | Phòng đã đủ người hoặc không ở trạng thái `WAITING` |
| `MATCH_NOT_FOUND` | `matchId` không phải trận hiện tại của người gửi |
| `MATCH_NOT_IN_PROGRESS` | Trận đã kết thúc hoặc đang tạm dừng |
| `NOT_YOUR_TURN` | Chưa đến lượt |
| `INVALID_POSITION` | Tọa độ nằm ngoài bàn cờ |
| `CELL_OCCUPIED` | Ô đã có quân |
| `FORBIDDEN` | Không đủ quyền (REST admin) |
| `INTERNAL_ERROR` | Lỗi không lường trước ở server |

## 7. Ví dụ phiên làm việc qua TCP

Có thể dùng `ncat localhost 9090` (đi kèm Nmap) hoặc `telnet localhost 9090`. Dòng `>` là client gửi, dòng `<` là server trả.

```text
> {"type":"LOGIN","reqId":"1","data":{"username":"alice","password":"secret1"}}
< {"type":"OK","reqId":"1","data":{"token":"eyJ...","user":{"id":1,"username":"alice",...}}}
> {"type":"CREATE_ROOM","reqId":"2","data":{"name":"Vào đây"}}
< {"type":"ROOM_LIST","data":{"rooms":[...]}}
< {"type":"OK","reqId":"2","data":{"room":{"id":"R1","status":"WAITING",...}}}
            ... bob gửi JOIN_ROOM R1 ...
< {"type":"ROOM_UPDATE","data":{"room":{"id":"R1","status":"PLAYING",...}}}
< {"type":"MATCH_START","data":{"matchId":"6f1c...","yourMark":"X","turn":"X",...}}
> {"type":"MOVE","reqId":"3","data":{"matchId":"6f1c...","x":7,"y":7}}
< {"type":"MOVE_APPLIED","data":{"moveNo":1,"x":7,"y":7,"mark":"X","nextTurn":"O",...}}
< {"type":"OK","reqId":"3","data":{"moveNo":1}}
> {"type":"PING","reqId":"4"}
< {"type":"PONG","reqId":"4","data":{"serverTime":1761200000000}}
```

Lưu ý: sự kiện push (như `MOVE_APPLIED`) có thể đến **trước** `OK` của chính yêu cầu đã gây ra nó.
Client phải xử lý thông điệp theo `type` và `reqId`, không được giả định thứ tự.

## 8. Dự kiến bổ sung (chưa triển khai)

`LIST_ONLINE` / `ONLINE_USERS`, `INVITE` / `INVITED` / `INVITE_REPLY`, `REMATCH`. Các thông điệp này sẽ
được đặc tả ở đây trước khi code.
