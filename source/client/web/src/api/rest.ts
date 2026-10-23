import type {
  AdminUserView,
  HistoryItem,
  LeaderboardEntry,
  MatchDetail,
  MatchSummary,
  Page,
  Profile,
  RoomInfo,
  SystemStats,
  UserInfo,
} from './protocol';
import { ServerError } from './protocol';

/** REST API (docs/protocol.md, section 1). Errors come back as {"code","message"}. */
async function call<T>(method: string, path: string, token: string | null, body?: object): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers: {
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ServerError('DISCONNECTED', 'Không kết nối được tới server');
  }
  const text = await response.text();
  const json = text ? JSON.parse(text) : {};
  if (!response.ok) {
    throw new ServerError(json.code ?? String(response.status), json.message ?? `Lỗi HTTP ${response.status}`);
  }
  return json as T;
}

export const rest = {
  register: (username: string, password: string, displayName?: string) =>
    call<UserInfo>('POST', '/auth/register', null, { username, password, displayName }),
  login: (username: string, password: string) =>
    call<{ token: string; user: UserInfo }>('POST', '/auth/login', null, { username, password }),

  profile: (token: string, userId: number) => call<Profile>('GET', `/users/${userId}`, token),
  history: (token: string, userId: number, page: number, size = 15) =>
    call<Page<HistoryItem>>('GET', `/users/${userId}/matches?page=${page}&size=${size}`, token),
  match: (token: string, matchId: string) => call<MatchDetail>('GET', `/matches/${matchId}`, token),
  leaderboard: (token: string, page: number, size = 20) =>
    call<Page<LeaderboardEntry>>('GET', `/leaderboard?page=${page}&size=${size}`, token),

  admin: {
    stats: (token: string) => call<SystemStats>('GET', '/admin/stats', token),
    users: (token: string, q: string, page: number) =>
      call<Page<AdminUserView>>('GET', `/admin/users?q=${encodeURIComponent(q)}&page=${page}&size=15`, token),
    lock: (token: string, userId: number) => call<AdminUserView>('POST', `/admin/users/${userId}/lock`, token),
    unlock: (token: string, userId: number) => call<AdminUserView>('POST', `/admin/users/${userId}/unlock`, token),
    rooms: (token: string) => call<RoomInfo[]>('GET', '/admin/rooms', token),
    matches: (token: string, page: number) =>
      call<Page<MatchSummary>>('GET', `/admin/matches?page=${page}&size=15`, token),
  },
};
