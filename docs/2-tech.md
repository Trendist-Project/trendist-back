# Trendist 2부. 기술

> 문서 목록과 변경 이력은 [docs/README.md](README.md). 절 번호(1~17)는 네 문서에 걸쳐 이어지고, 코드 주석의 `(4.8)` 같은 번호가 이 번호다.

## 7. 기술 스택

| 영역 | 선택 |
|---|---|
| 프론트엔드 | React + TypeScript, Vite. 아레나는 Canvas 2D(부족하면 PixiJS) |
| 백엔드 | Spring Boot 4.1.2, Java 25, Gradle 9.8.2(Kotlin DSL). REST API, WebSocket 게임 서버, 배치를 한 애플리케이션으로 시작 |
| 실시간 통신 | Spring WebSocket(순수 WebSocket, 바이너리 프레임), 서버 권위. 20Hz 시뮬레이션, 10~15Hz 스냅샷. STOMP 미사용 |
| DB | MySQL 8, InnoDB, `utf8mb4`, UTC 저장. 개발·테스트는 JPA가 스키마를 자동 반영하고(11.3), 운영은 Flyway. 로컬 개발은 우분투의 MariaDB 10.11(DB `trendist`), 자동 테스트는 H2 메모리 DB |
| 캐시 | Caffeine(서버 1대). 여러 대가 되면 Redis |
| 배치 | Spring `@Scheduled` + ShedLock |
| 이미지 | S3 호환 객체 저장소(초기에는 로컬 디스크 가능), 자동 검열은 ONNX Runtime(Java) |
| 인증 | 게스트는 방문자 ID, 계정은 Google ID 토큰 서버 검증으로 식별하고 액세스 JWT(15분) + 갱신 토큰(1일, 회전)을 쓴다(Spring Security) |
| 배포 | Docker, 앞단 Nginx(정적 파일, WebSocket 프록시, TLS). 클라우드 미정 |

## 8. 프론트엔드
- `<canvas>` 하나 위에 React UI(검색, 보관함, 랭킹, 카드, 팝업, 채팅, 미니맵)를 겹친다.
- 스냅샷은 React state에 넣지 않고 렌더링 루프가 쓰는 ref에 둔다. state에는 내 점수, 열린 패널처럼 가끔 바뀌는 값만 둔다.
- 내 아이콘은 입력을 바로 반영해 미리 움직이고(클라이언트 예측) 서버 위치로 부드럽게 보정한다. 다른 아이콘은 스냅샷 사이를 보간한다.
- 내 아이콘 떨림 방지(빠른 연타에서도 화면이 떨리지 않게)
  - 예측은 서버와 같은 규칙으로 움직인다: 같은 최대 속도·감속 거리, 50ms마다 목표 속도와의 차이가 절반이 되는 가속. 양쪽 모두 이 지수 곡선을 정확한 식으로 풀어서(프레임·틱 간격과 관계없이 같은 결과) 같은 입력이면 같은 곳에 멈춘다.
  - 조작은 바뀐 시각에 정확히 적용한다. 클라이언트는 조작이 바뀌는 순간까지 예측을 먼저 진행한 뒤 바꾸고(프레임 경계로 반올림하지 않음), 서버는 조작이 도착한 시각을 기록해 틱 구간을 그 시점에서 나눠 움직인다(틱 경계로 반올림하지 않음). 그래서 방향키를 짧게 눌렀다 떼거나 두 개를 함께 눌러도 서버와 예측이 같은 곳에 멈추고, 멈춘 뒤 원이 혼자 미끄러지지 않는다.
  - 이동 목표와 방향 입력은 서버로 보내는 순간(클릭은 초당 10회로 묶은 시점, 방향키는 바뀐 즉시, 한 프레임 16ms 안의 변화만 합침)에 예측에도 반영한다. 클릭 물결 효과만 클릭 즉시 보여 준다.
  - 서버 위치는 왕복 시간만큼 늦은 과거 값이라 매번 당기지 않는다. 움직이는 중에는 최대 속도 × (0.25초 + 왕복 시간) 넘게, 멈춰 있으면 3px 넘게 어긋날 때만 맞춘다(지연이 들쭉날쭉해 생기는 1~2px 차이로 멈춘 원이 다시 미끄러지지 않게). 왕복 시간은 2초마다 `ping`/`pong`으로 잰다.
  - 맞출 때는 예측 위치만 서버 위치로 옮기고, 화면 위치(카메라)는 순간 이동 없이 약 0.12초에 걸쳐 따라간다. 다만 둘 다 멈춰 있을 때 생긴 작은 차이(12px 이하)는 화면에 바로 보여 주지 않고, 다음에 움직이기 시작할 때 섞어서 따라간다(멈춘 원이 혼자 한 번 움직여 보이지 않게). 서버 쪽 원이 움직이는 중(튕김 등)이거나 12px보다 크게 어긋나면 멈춰 있어도 바로 따라간다. 300px 넘게 어긋나면(입장·순간 이동) 바로 맞춘다.
- 모바일 끊김 방지
  - 다른 원은 서버 시각(스냅샷의 틱 번호 × 50ms)으로 140ms 늦게, 앞뒤 스냅샷 사이를 보간해 그린다. 도착 간격이 들쭉날쭉해도 멈췄다 튀지 않는다. 서버 시각과 브라우저 시각의 차이는 가장 빨리 도착한 스냅샷 기준으로 잡는다.
  - 내 원의 예측은 서버 틱처럼 50ms씩 나눠 진행하고, 프레임이 늦게 와도 그 시간을 버리지 않는다(최대 1초). 예전에는 0.1초를 넘는 시간을 버려 서버보다 뒤처지고 보정으로 화면이 튀었다.
  - 1,500px 넘게 어긋날 때만 바로 맞추고, 그보다 작으면 화면을 부드럽게 따라가게 한다(튕김·지연으로 수백 px 어긋날 때 순간 이동하지 않게).
  - 캔버스 크기는 정수로 맞추고 DPR은 2까지만 쓴다(DPR이 소수인 휴대폰에서 매 프레임 캔버스를 새로 만들던 문제). 원 그림자는 `shadowBlur` 대신 반투명 원, ? 구슬은 한 번 그린 그림을 찍는다. 그리다 오류가 나도 다음 프레임은 계속 돈다.
- 바닥 격자는 작은 타일을 한 번 그려 반복한다. 화면 밖 아이콘은 그리지 않고 이미지는 한 번 받아 캐시한다.
- 서버 데이터는 TanStack Query, 전역 UI 상태는 Zustand(후보, 버전 고정).
- 다국어: react-i18next로 언어별 JSON. 빠진 번역은 영어(영어 지원 전에는 한국어)로 대체, 빌드 때 누락 키 검사. DB의 이름(칭호, 키워드·사용자 상태, 나라)은 한글 하나만 두고 다른 언어는 키로 찾는다(칭호·키워드 상태는 ID, 예: `titles.7.name`, 나라는 `countries.code`). ID는 초기 데이터를 넣는 순서로 정해지므로 순서를 바꾸거나 지우지 않는다(안 쓰는 칭호는 `enabled`를 끈다). 서버 오류는 코드와 파라미터로만 받는다.
- 날짜·숫자는 `Intl` API, 폰트는 Noto Sans(KR·JP·SC·Devanagari)를 필요한 것만 불러온다.
- 키워드 활동 그래프(4.12): SVG로 직접 그리거나 가벼운 차트 라이브러리(후보 uPlot·Recharts, 버전 고정)를 쓴다. 응답은 TanStack Query로 1분 캐시한다.
- 접근성: 키보드 포커스, 화면 낭독기 라벨(예: "거북이 아이콘 5단계"), 그래프 요약 문장.

## 9. 백엔드

### 9.1 모듈

| 모듈 | 역할 |
|---|---|
| api | 검색, `+`, 보관함, 댓글, 랭킹, 정보 팝업, 점수 추이, 칭호, 공지, 가이드, 탈퇴 |
| game | 나라별 방, 입장 인원 검사, 게임 루프, 충돌, 관심 영역, 스냅샷 |
| trend | 외부 수집, 외부 확인과 반영 소스 결정, 트렌드 값 계산, 상태 전이, 나라 현지 시각 배치 |
| admin | 금지, 가중치, 밸런스, 칭호, 공지, 가이드, 신고·이미지 검토, 트렌드 키워드 목록 |
| auth | 방문자 ID 필터, Google 로그인, 권한 |

### 9.2 게임 서버
- 방 하나 = `Room` 객체 하나. 방마다 플랫폼 스레드 하나가 20Hz(50ms) 고정 틱을 돈다. 입력은 큐로 받아 틱 시작에 처리하므로 락이 없다. 이동 조작(방향키·클릭)은 도착 시각을 함께 넣고, 틱은 지난 틱부터 지금까지의 구간을 도착 시점마다 나눠 계산한다.
- `WebSocketHandler`로 직접 받고 스냅샷은 `ByteBuffer` 바이너리(또는 Protocol Buffers)로 보낸다. 스냅샷에는 세션 엔티티 번호, 위치, 반지름, 현재 유행도(4바이트), 레벨, 칭호 번호, 장착 프로필 번호(`profile_id`), 프로필 종류, 바탕색, 테두리 색과 두께만 싣는다(픽셀·이미지 프로필의 그림은 프로필 번호로 내려받는다). 세션 엔티티 번호는 방에 들어올 때 정해져 그 방에서만 유효하고, 정보 팝업·디자인 복사처럼 다른 플레이어를 가리키는 요청은 이 번호로 하며 서버가 방 상태에서 사용자로 바꾼다.
- 관심 영역: 화면 주변 엔티티만 보낸다(1000명 전원이면 클라이언트당 초당 약 180KB, 주변 50명이면 약 9KB). 영역은 그 사람의 줌 배율(4.2)에 맞춰 넓히고, 영역 안 엔티티 수에 상한(예: 가까운 순 150명)을 둔다. 반지름이 클수록 보이는 면적이 커져(배율 0.37이면 약 7배, 최대 0.23이면 약 19배) 보낼 엔티티가 늘기 때문이다. 상한 값은 부하 테스트로 정한다. 점수 4바이트를 더하면 엔티티당 스냅샷이 20~30% 커진다(위 추정치 기준 계산이고 부하 테스트로 확인한다).
- 충돌은 공간 격자로 가까운 아이콘끼리만 검사한다. 격자 칸 크기는 일반 크기 기준이고, 큰 아이콘(반지름 600px까지)은 걸치는 칸 모두에 등록한다.
- 충돌로 키워드 잃기(4.3): 틱에서 충돌을 찾으면 두 사람의 현재 유행도를 메모리 값으로 비교하고, 보호 조건(잃은 뒤 10초, 입장 직후 10초, 봇)을 사용자별 보호 종료 시각 하나로 검사한다. 통과하면 "키워드 잃기" 이벤트만 큐에 넣고 틱은 계속 돈다. 실제 처리는 9.4를 따른다.
- 나가기: 페이지를 떠날 때(구글 로그인으로 이동, 새로고침, 탭 닫기, 로그아웃) 클라이언트가 `bye`를 보내 바로 방에서 빠진다. 네트워크가 끊겼는데 닫힘 신호가 오지 않거나 중간 프록시가 서버 쪽 연결을 남겨 두는 경우를 위해, 90초 동안 아무 메시지(2초마다 보내는 ping 포함)도 없는 접속은 방에서 빼고 연결을 닫는다(백그라운드 탭은 브라우저가 타이머를 1분 간격까지 늦춘다). 그러지 않으면 랭킹·방에 유령 접속자가 남는다.
- 느린 클라이언트는 송신 버퍼 상한을 넘으면 오래된 스냅샷을 버린다. DB·외부 호출은 틱 스레드 밖에서 가상 스레드로 처리한다.
- 방 관리자와 입장 라우팅은 인터페이스로 분리해 두고, 나중에 지역당 여러 방·여러 서버로 늘린다.
- 설정값 전달: 이동 계산은 항상 서버가 `settings`(1분 캐시)의 값으로 한다. 클라이언트가 예측에 쓰는 값(최대 속도, 반지름·줌 곡선 값)은 입장할 때 서버가 보내 주고, 관리자가 바꿔서 설정이 갱신되면 방 안 모든 클라이언트에 다시 보낸다. 그러지 않으면 내 아이콘 예측이 서버 위치와 어긋난다.
- 채팅(4.13)도 게임 WebSocket으로 받는다(`chat{text}`). 검사는 받는 스레드에서, 저장은 작업 스레드에서 하고, 저장이 끝나면 방에 넣어 틱 스레드가 방 전체에 `chat{id, e, nickname, text, at}`을 보낸다. 방은 최근 50개를 메모리에 두고 들어오는 사람에게 `chatHistory`로 보낸다. 보낸 사람은 세션 엔티티 번호(`e`)로만 가리킨다.

점수 실시간 반영
키워드의 `trend_value`가 바뀌면(`+`, 24시간 삭제, 충돌로 잃기, 외부 수집, 설정 변경) 9.4의 트랜잭션이 끝난 뒤 (키워드 번호, 변화량) 이벤트를 그 방의 큐에 넣는다.
1. 방 메모리에 키워드별로 접속 중인 보유자 색인(키워드 번호 → 플레이어 집합)을 둔다. 입장, 퇴장, `+`, 삭제, 잃기 때 갱신하므로 DB 조회가 필요 없다.
2. 틱이 같은 틱 안에 쌓인 변화량을 키워드별로 합쳐서, 그 키워드의 접속 중인 보유자 각각의 점수에 합친 변화량을 더하고 반지름을 다시 구한다. 변화량은 보유자 모두에게 같아서 계산이 덧셈 한 번이다.
3. 다음 스냅샷(10~15Hz)에 새 반지름과 현재 유행도가 실려 나가고, 클라이언트는 0.5초에 걸쳐 크기를 바꾼다. 자기 점수·레벨 같은 UI 값은 내 클라이언트에 점수 변경 메시지로 따로 보낸다(1초에 한 번까지 합쳐서).
4. 한 키워드에 `+`가 몰려도 틱당 한 번만 계산하고, 클라이언트가 받는 갱신은 스냅샷 주기가 상한이라 `+` 횟수와 상관없이 부하가 제한된다. 보유자는 키워드당 최대 방 인원 상한(1,000명)이다.
5. 방 메모리 점수는 접속 중인 플레이어의 기준값이고, DB의 `players.current_score`는 바뀐 사람만 5초마다 모아서 저장한다(접속 중인 플레이어의 DB 값은 최대 수 초 늦을 수 있다). 접속을 끊을 때 마지막 값을 저장하고, 접속할 때는 DB 합산값(9.4)으로 시작한다.
6. 접속 중이 아닌 보유자는 방 메모리에 없으므로 9.4의 1분 재합산이 맡는다.

### 9.3 인증과 방문자 ID
- `UUID.randomUUID()`(v4) + `DateTimeFormatter.ofPattern("yyyyMMddHHmmss")`, `ZoneOffset.UTC`. 예: `20261013001329-3f2b8c1e-7a4d-4e9b-b0c2-91d5e6a7f8b3`(51자).
- 검증 정규식: `^\d{14}-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$`. 모르는 값이나 형식 오류면 쿠키를 지우고 401을 돌려준다(새 ID는 [시작]을 누를 때 발급한다). 클라이언트가 만든 값은 받지 않는다.
- 쿠키: `HttpOnly`, `Secure`, `SameSite=Lax`, 1년. 서블릿 필터 하나에서 발급·검증한다.
- 액세스 토큰: JWT, 15분. `sub`에는 방문자 ID가 아니라 사용자 내부 번호(`users.id`)를 넣는다. 브라우저 메모리에만 두고 REST는 `Authorization: Bearer`로 보낸다. 수명과 값은 기본값이다.
- 갱신 토큰: 서버가 만든 무작위 값(32바이트 이상)을 `HttpOnly`, `Secure`, `SameSite=Lax` 쿠키(경로 `/`)로 준다. 경로를 `/api/auth`로 좁히면 개발자 도구 쿠키 목록에 안 보이고 쿠키 지우기로도 지워지지 않아 `/`로 둔다. 1일이고 쓸 때마다 새 값으로 교체(회전)하며 만료도 1일 뒤로 늦춘다. 하루 넘게 접속하지 않으면 만료되어 구글로 다시 로그인해야 한다(게스트는 방문자 ID로 이어진다). DB(`refresh_tokens`)에는 SHA-256 해시만 둔다. 이미 폐기된 토큰이 다시 오면 탈취로 보고 그 사용자의 갱신 토큰을 모두 폐기한다.
- 게스트: 방문자 ID 쿠키(`visitor_id`, HttpOnly)가 오래 가는 신원이다. [시작]을 누르면 `POST /api/auth/guest`가 방문자 ID를 발급하고 게스트 행을 만들어 액세스 토큰을 준다(유효한 방문자 ID 쿠키가 이미 있으면 새로 만들지 않고 그 행을 쓴다). 이후 토큰이 없거나 만료되면 `POST /api/auth/session`이 방문자 ID로 사용자를 찾아 발급하고, 찾지 못하면 401을 돌려 메인 화면을 보여 준다. 방문자 ID로는 구글이 연결되지 않은 게스트 행만 토큰을 받을 수 있다. 계정은 갱신 토큰으로만 이어 간다.
- 로그인: `POST /api/auth/google`에서 구글 ID 토큰을 검증한다. 구글 계정이 아직 어디에도 연결되지 않았으면 지금 게스트 행에 연결하고(게스트 행이 없으면 새 계정 행을 만든다), 이미 있는 계정이면 그 계정으로 전환한다(탈퇴 상태인 계정은 아래 복구 확인을 거친다). 어느 쪽이든 계정의 액세스·갱신 토큰을 발급하고 브라우저의 방문자 ID 쿠키를 지운다. 그래서 갱신 토큰이 끝난 뒤에도 옛 게스트 행으로 되돌아가지 않는다.
- 구현: 버튼을 누르면 구글 계정 선택 페이지(`https://accounts.google.com/o/oauth2/v2/auth`, `response_type=id_token`, `scope=openid`, `prompt=select_account`)로 이동한다. `prompt=select_account`라 브라우저에 로그인된 계정이 자동으로 쓰이지 않고 매번 계정을 고르거나 다른 계정을 추가할 수 있다. 시작할 때 만든 state·nonce를 탭 저장소(sessionStorage)에 두고, 돌아오는 `/oauth/google-callback.html`이 state를 비교한 뒤 ID 토큰을 넘기면 앱이 `POST /api/auth/google {idToken, nonce}`로 보낸다(서버는 토큰 안의 nonce와 비교). 게임 중에 로그인해도 페이지를 다녀와 바로 게임으로 다시 들어온다. 서버는 구글 공개 키(JWKS, `https://www.googleapis.com/oauth2/v3/certs`)로 서명을 확인하고 발급자(accounts.google.com)·대상(우리 클라이언트 ID, 설정 `trendist.google.client-id`)·만료를 본 뒤, 이 사이트의 JWT(액세스 토큰, `sub`=`users.id`)와 갱신 토큰 쿠키(`refresh_token`, 경로 `/`)를 준다. 이후 모든 API·WebSocket은 이 사이트의 JWT로만 인증하고 구글 토큰은 다시 쓰지 않는다. 클라이언트 보안 비밀번호는 쓰지 않으므로 코드·설정·저장소 어디에도 두지 않는다. 구글 콘솔의 OAuth 클라이언트(웹 애플리케이션)에 "승인된 리디렉션 URI"로 `사이트 주소/oauth/google-callback.html`(로컬은 `http://localhost:8080/oauth/google-callback.html`)을 넣어야 한다.
- 탈퇴 계정 복구: 로그인한 구글 계정이 탈퇴 상태이면 서버는 토큰을 발급하지 않고 `{ "status": "WITHDRAWN" }`을 돌려준다. 클라이언트가 복구 여부를 묻고, 확인하면 같은 구글 ID 토큰으로 `POST /api/auth/google/restore`를 부른다. 서버가 토큰을 다시 검증하고, 탈퇴 상태일 때만 상태를 정상으로 되돌리고 `deleted_at`을 비운 뒤 액세스·갱신 토큰을 발급한다(탈퇴 상태가 아니면 거절). 취소하면 요청을 보내지 않으므로 아무것도 바뀌지 않는다.
- 로그아웃·탈퇴·계정 금지: 그 사용자의 갱신 토큰을 폐기한다. 로그아웃은 `users.tokens_revoked_at`에 시각을 남겨, 그보다 먼저 발급한 액세스 토큰(JWT의 `iat`)을 남은 수명과 관계없이 바로 거절한다(입장·WebSocket·API 모두, 30초 캐시를 지운다). 액세스 토큰이 살아 있는 15분 동안도 막으려고 입장, WebSocket 연결, 민감한 요청에서는 `banned_until`과 사용자 상태(`user_state_id`)를 짧은 캐시(Caffeine)로 확인하고, 접속 중이면 연결을 끊는다. 탈퇴 상태인 사용자는 구글 로그인 후 복구 확인으로만 돌아올 수 있다(4.1).
- WebSocket: 토큰을 연결 주소의 쿼리에 담지 않고(로그에 남는다) 연결 직후 첫 메시지로 액세스 토큰을 보낸다. 5초 안에 인증하지 못하면 끊는다. 이미 연결된 뒤에 토큰이 만료되어도 그 연결은 유지하고, 다시 접속할 때 새 토큰을 쓴다.
- 갱신 요청은 `POST`이고 `SameSite=Lax` 쿠키라 다른 사이트에서 보낼 수 없다. CORS는 우리 도메인에만 허용한다. 방문자 ID와 토큰은 URL·쿼리·로그에 넣지 않는다.
- OAuth 제공자의 사용자 ID(구글은 `sub`)는 SHA-256 해시로만 저장하고, 제공자 종류와 함께 `oauth_type`·`oauth_id`에 둔다. 금지된 계정(`users.banned_until`이 지금보다 뒤)은 입장과 WebSocket 연결을 막는다. 관리자 API는 `ROLE_ADMIN`을 서버에서 검사한다.

### 9.4 `+` 처리와 점수 재합산
`+` 처리
- 한 트랜잭션: `players` 행을 `SELECT ... FOR UPDATE`로 먼저 잠그고, `player_keywords`에 INSERT(또는 만료 행 재사용, 아래)한다(`expires_at` = 지금 + 24시간). 그다음 `keywords` 행의 `holder_count`·`total_plus_count`를 고치고 `trend_value`를 다시 계산한다. 마지막으로 `players.exp` +1(레벨은 메모리에 올려 둔 `levels` 표로 다시 정함), `players.current_score`에 그 키워드의 새 점수(`trend_value`)와 늘어난 EXP 점수(100 미만일 때 1)를 더하고, `total_plus_count` +1, 새 행이면 `hold_count` +1 한다. 잠금 순서(플레이어 → 키워드)는 항상 같게 둔다. 접속 중이면 방 메모리 점수도 같이 고친다.
- 같은 키워드 중복은 `UNIQUE(player_id, keyword_id)`가 막는다. 화면의 `+` 버튼은 비활성화되어 있고, 서버는 만료 전 행이 있으면 요청을 거부한다.
- 만료 행 재사용: `expires_at`이 지났지만 1분 잡이 아직 지우지 않은 행이 있으면 INSERT하지 않고 그 행을 UPDATE한다(`created_at`과 `expires_at`을 새 `+` 값으로 바꾼다). 행 수가 그대로이므로 `keywords.holder_count`와 `players.hold_count`는 바꾸지 않고, 두 `total_plus_count`만 +1 하며, `current_score`는 그 사람의 목록을 다시 합산해서 구한다. `created_at`을 갱신하므로 이 엔티티는 JPA Auditing의 `updatable = false`를 쓰지 않는다.
- 1분 주기 잡이 `expires_at`이 지난 행을 삭제하고 `keywords.holder_count`와 `players.hold_count`를 −1(0 아래로 내려가지 않게), `trend_value`를 다시 계산한 뒤 그 플레이어의 점수를 다시 합산한다. 같은 `players` 행을 잠그고 `DELETE ... WHERE id = ? AND expires_at <= 지금`처럼 조건부로 지워서, 방금 재사용된 행을 지우거나 두 번 처리하지 않는다. 조회할 때도 `expires_at`을 검사한다.
- 충돌로 잃기: 틱 밖의 작업 스레드가 잃는 사람의 후보 행(키워드 미금지) 중 하나를 무작위로 골라 DELETE하고, 같은 트랜잭션에서 `holder_count`·`hold_count`를 −1, `trend_value` 재계산, 그 플레이어의 점수 재합산을 한다. 행이 지워지므로 바로 다시 `+`할 수 있다. 끝나면 방에 점수 변경을, 잃은 사람에게만 알림을 보낸다. 후보가 없으면(`hold_count`가 0이면 조회 없이) 아무것도 하지 않는다.
- 충돌로 빼앗기(사람끼리): 위 잃기와 같은 트랜잭션에서 이긴 사람에게 그 키워드를 준다. 두 `players` 행을 번호 순서로 먼저 잠그고(서로 동시에 빼앗아도 교착하지 않게) 키워드 행은 그다음에 잠근다. 이긴 사람에게 만료 전 행이 있으면 `created_at`·`expires_at`만 지금 기준으로 갱신하고, 없으면 `+` 처리와 같이 넣는다(만료 행 재사용 포함). 잃은 사람 −1, 이긴 사람 +1이라 `holder_count`와 트렌드 값은 결국 그대로다.

트렌드 값과 점수 갱신
- 트렌드 값 재계산: 한 키워드의 `trend_value`는 4.8의 식으로 `holder_count`, 반영 소스(`data_source_id`)의 가중치, 상태에서 구한다. 값이 바뀌면 (키워드 번호, 변화량)을 두 곳에 알린다. 접속 중인 보유자에게는 방 메모리 이벤트로 바로(9.2), 접속 중이 아닌 보유자에게는 메모리의 "변경됨" 집합으로. 외부 수집, 💤↔🔥 전환, 가중치·설정 변경 때도 같은 방식이다.
- 활성 전환: `+`로 가진 사람이 3명이 되거나 구글 실시간 인기 목록에서 외부 확인이 되면 `UPDATE keywords SET keyword_state_id = /* 활성 */, activated_at = COALESCE(activated_at, 지금) WHERE id = ? AND keyword_state_id = /* 비활성 */`을 하고, 영향 행 수가 1일 때만 그 키워드를 "변경됨"에 넣는다(동시에 `+`해도 한 번만). 접속 중인 보유자의 점수는 바로, 아닌 보유자는 다음 재합산에서 반영된다. 다른 사용자의 `players` 행을 한 트랜잭션에서 한꺼번에 잠그지 않으려는 것이다.
- 점수 재합산(1분): "변경됨" 집합을 비우면서 그 키워드를 가진 접속 중이 아닌 플레이어를 찾아(`idx_player_keywords_keyword`), 500명씩 묶어 한 SQL로 다시 합산한다. 값이 바뀐 사람만 저장한다. 합산 대상은 플레이어의 `players` 행을 UPDATE로 잠그는 구간뿐이고 키워드 행은 읽기만 하므로 `+` 트랜잭션과 교착하지 않는다. LEFT JOIN이라 보관함이 모두 없어진 플레이어도 EXP 점수만 남도록 계산된다.

```sql
UPDATE players p
LEFT JOIN (
  SELECT pk.player_id, COALESCE(SUM(k.trend_value), 0) AS score   -- 키워드 점수 = 트렌드 값
  FROM player_keywords pk JOIN keywords k ON k.id = pk.keyword_id
  WHERE pk.player_id IN (/* 500명 */) AND pk.expires_at > NOW(6) AND k.keyword_state_id = /* 활성 상태 번호 */
  GROUP BY pk.player_id
) t ON t.player_id = p.id
SET p.current_score = LEAST(p.exp, 100) + COALESCE(t.score, 0)           -- 100은 EXP 점수 반영 상한(설정값)
WHERE p.id IN (/* 500명 */)
  AND p.current_score <> LEAST(p.exp, 100) + COALESCE(t.score, 0);
```

- 전체 점검(1시간): 보관함이 있는 플레이어 전원을 같은 방식으로 다시 합산하고, `hold_count`도 만료되지 않은 `player_keywords` 행 수로 맞춘다. 접속 중인 플레이어는 합산 결과와 방 메모리 값을 비교해서 다르면 메모리를 고친다. 행이 하루치뿐이라 부담이 작다(베타에서 측정).

### 9.5 요청 빈도 제한
- Caffeine 메모리 + 슬라이딩 윈도 로그(키마다 최근 요청 시각 N개). 통과한 요청만 기록한다. 서버가 여러 대가 되면 Redis로 옮긴다.
- 거부하면 HTTP 429, 오류 코드, `Retry-After`.

| 대상 | 한도 |
|---|---|
| API 전반 | 초당 5회 수준(정상 플레이에서 걸리지 않게) |
| 이동 명령 | 클릭 이동 초당 10회, 방향키는 바뀔 때마다(16ms에 한 번까지, 클라이언트 기준). 서버는 종류별 토큰 버킷으로 센다: 클릭 초당 12개(최대 24개까지 모임), 방향키 초당 60개(최대 60개), 그 밖의 종류 초당 10개. 고정 1초 창으로 세면 네트워크·프록시가 메시지를 몰아서 전달할 때 한도를 넘겨 그 1초가 끝날 때까지 모든 명령이 버려지고, 화면만 움직였다가 되돌아오는 문제가 있었다. `ping`은 세지 않는다 |
| 채팅 | 1초에 1회(게임 WebSocket 연결마다 토큰 버킷, 넘으면 `chatError` `CHAT_TOO_FAST`) + IP 단위 |
| 정보 팝업 조회 | 계정당 초당 2회, 결과 5초 캐시 |
| 댓글 투표 | 사용자당 분당 20회, 같은 댓글 분당 5회(`vote:comment:{user}:{comment}`) |
| 색·칭호 변경 | 5초에 1회 |
| 이미지 변경 | 1시간에 1회(DB의 `image_changed_at`으로 확인, 재시작해도 유지) |
| 외부 온디맨드 조회, 새 키워드 생성 | 사용자·IP 단위 분당 제한 |
| 트렌드 상자 열기 | 초당 2회 |
| 토큰 발급·갱신·게스트 시작(`/api/auth/*`) | IP 단위 분당 10회 |

### 9.6 API (초안)

| API | 내용 |
|---|---|
| `POST /api/auth/guest` | [시작]. 방문자 ID를 발급하고 게스트 행을 만들어 액세스 토큰을 발급한다. 유효한 방문자 ID 쿠키가 있으면 그 게스트를 쓴다 |
| `POST /api/auth/session` | 갱신 토큰 쿠키가 있으면 회전하고 새 액세스 토큰을 발급한다. 없으면 방문자 ID로 게스트를 찾아 발급하고, 찾지 못하면 401 |
| `POST /api/auth/google` | `{ "id_token": "..." }`. 구글 계정을 연결하거나 전환하고 액세스·갱신 토큰을 발급한다. 탈퇴한 계정이면 토큰 없이 `{ "status": "WITHDRAWN" }`을 돌려준다 |
| `POST /api/auth/google/restore` | `{ "id_token": "..." }`. 탈퇴한 계정을 복구하고(사용자가 확인한 뒤에만 호출) 액세스·갱신 토큰을 발급한다. 탈퇴 상태가 아니면 거절 |
| `POST /api/auth/logout` | 갱신 토큰을 폐기하고 쿠키를 지운다(204) |
| `POST /api/comments/{id}/vote` | `{ "vote": "LIKE" \| "DISLIKE" }`. 응답은 내 상태와 `like_count`, `dislike_count` |
| `GET /api/keywords/{id}/comments/top` | 추천 상위 3개(10초 캐시) |
| `GET /api/keywords/{id}/comments?page=&size=` | 최신순, `size` 최대 50 |
| `GET /api/keywords/{id}/activity?range=24h\|7d\|30d\|180d` | 검색 수·`+` 수·가진 사람 수 증분(24시간은 시간별, 나머지는 현지 하루별)과 누적값(1분 캐시). 이번 시간은 메모리 카운터를 합쳐 "집계 중"으로 표시 |
| `PATCH /api/me/nickname` | `{ "nickname": "..." }`. 현재 나라 닉네임을 바꾼다(4.1). 규칙 위반 400, 지금과 같으면 400 `NICKNAME_SAME`, 변경 간격 전이면 409 `NICKNAME_TOO_SOON`. 응답은 플레이어 요약이고 `nicknameChangeableAt`(다음 변경 가능 시각, 없으면 바로 가능)을 담는다 |
| `POST /api/me/profile/copy` | `{ "profile_id": 123 }`. 내가 접속한 방 플레이어가 장착 중인 프로필만 복사할 수 있다. 바탕색·테두리 색·테두리 두께를 내 새 프로필(성장형)로 만들어 장착하고(픽셀 그림·업로드 이미지 제외), 레벨 조건에 맞지 않으면 거절한다 |
| `GET /api/notices` | 공지 목록(고정 먼저, 최신순, 최대 50). `edited`(수정됨), `fresh`(7일 이내, NEW). 로그인 없이 읽는다 |
| `GET /api/notices/popup`, `POST /api/notices/popup/seen` | 팝업 공지 목록(최대 10), 본 기록(멱등) |
| `GET /api/me/score-history?range=24h\|7d\|30d\|all` | 내 점수 추이(현재 나라). 점 `{t, score, live}`, 기록이 없는 칸은 직전 값, 마지막 점은 지금 값 |
| `GET /api/room/players/{entity_no}` | 같은 방 다른 플레이어 정보(4.11): 닉네임, 관리자 여부, 활성 칭호, 레벨, 현재 유행도, 댓글 점수, 보관함 상위 3개(이름·상태). 계정당 초당 2회(429 `INFO_TOO_FAST`) |
| `GET /api/room/players/{entity_no}/score-history?range=7d` | 내가 접속한 방의 다른 플레이어(7일만). 그 방에 없는 번호면 404. 7일 외의 range는 400 |
| `GET /api/me/admin` | 관리자인지만(`{ "admin": true }`). 관리자 화면과 게임의 [🛠 관리자] 버튼이 부른다 |
| `GET /api/admin/keywords?country=&state=&source=&q=&sort=&dir=&page=&size=` | 관리자 키워드 목록(13.6). state는 상태 이름, source는 소스 이름, sort는 trendValue·holderCount·totalPlusCount·searchCount·createdAt·activatedAt·externalUntil, 50개씩 |
| `POST /api/admin/keywords` | `{ "text": "...", "country": "KR" }`. 🔥활성 키워드 추가(반영 소스 admin, 7일, 13.7) |
| `POST /api/admin/keywords/{id}/ban`, `/unban` | 금지·해제(13.1). 금지하면 트렌드 값 0, 보유자 점수에서 바로 빠짐 |
| `GET /api/admin/reports?status=&page=`, `PATCH /api/admin/reports/{id}` | 신고 목록(대상 원문 포함, 30건씩)과 처리 상태 변경 `{ "status": "RESOLVED", "sameTarget": true }`(13.5) |
| `GET /api/admin/settings`, `PATCH /api/admin/settings/{name}` | 설정 목록과 값 변경 `{ "value": "12" }`. 숫자·정수·허용 범위 검사, 저장하면 캐시를 비워 바로 반영(13.3) |
| `GET /api/keywords/{id}` | 키워드 상세 카드(4.12). 내 나라 키워드만, 금지 키워드는 404 |
| `POST /api/keywords/{id}/comments` | `{ "body": "..." }`. 댓글 쓰기(1~200자, 최소 글자 수 제한 없음, 링크·전화번호 불가). 작성자 댓글 점수 +1 |
| `PATCH /api/comments/{id}`, `DELETE /api/comments/{id}` | 내 댓글 고치기(작성 후 5분 안, 지나면 409 `COMMENT_EDIT_EXPIRED`), 지우기(소프트 삭제, 댓글 점수 −1) |
| `POST /api/reports` | `{ "target": "COMMENT" \| "CHAT" \| "PLAYER", "targetId": 1, "reason": "..." }`(PLAYER는 같은 방의 세션 엔티티 번호). 신고(13.5). 내 나라 대상만, 내 글은 400 `REPORT_OWN`, 같은 대상 두 번은 409 `REPORT_DUPLICATE` |
| `GET /api/rankings?country=` | 리더보드: 그 나라 방에 접속 중인 사람의 현재 유행도 TOP 10(4.14) |
| `GET /api/guide/categories`, `/categories/{code}/articles`, `/articles/{id}`, `/search?q=` | 가이드(5분 캐시, 수정 시 즉시 삭제). 로그인 없이 읽는다. 지금은 앞의 두 개만 있고 글 목록에 본문을 같이 준다 |

- 투표 토글: 없음 → INSERT, 같은 값 → DELETE, 반대 값 → UPDATE. 조건부 쿼리의 영향 행 수가 1일 때만 `comments.like_count`·`dislike_count`를 바꾸고, 같은 때 작성자의 `comment_score`도 같이 바꾼다(추천 +1, 비추천 −1, 취소·변경은 반대로, `GREATEST(값, 0)`으로 0 아래 방지). 첫 투표 경합은 UNIQUE 오류 후 다시 읽어 처리한다.

### 9.7 배치 잡

| 주기 | 잡 |
|---|---|
| 5초 | 접속 중인 플레이어의 바뀐 점수를 `players.current_score`에 모아서 저장(9.2) |
| 1분 | 만료된 `+` 삭제와 `holder_count`·`hold_count` 정리, 트렌드 값이 바뀐 키워드의 접속 중이 아닌 보유자 점수 재합산(9.4) |
| 매시 정각 | 플레이어 점수 기록(`player_score_24h`의 그 시간 칸과 `player_score_dailies`의 그날 행을 현재 점수로 덮어쓰기), 키워드 카운터 저장(`keywords.search_count` 갱신 후 `keyword_log_24h`의 그 시간 칸 덮어쓰기, 그날의 `keyword_log_dailies` 다시 만들기), 180일이 지난 `keyword_log_dailies` 행 삭제(9.9), 전체 점수 재합산 점검(9.4) |
| 매일 | 30일 지난 채팅 삭제(04:30 KST, 신고된 메시지 제외), 만료된 갱신 토큰 삭제 |

### 9.8 트렌드 상자 (4.19)
상자는 몇 분만 사는 일회성 데이터라 DB에 저장하지 않고 방(`Room`) 메모리에서만 관리한다. 서버를 다시 켜면 상자는 사라지고 다음 주기에 새로 생긴다.

상태

```java
record TrendBox(
    long id,               // 방 안에서 증가하는 번호
    float x, float y,      // 월드 좌표
    long keywordId,        // 상자에 담긴 키워드(모든 사용자에게 같음)
    Instant expiresAt,     // 상자가 사라지는 시각
    Set<Long> openedBy     // 연 사용자 id(한 사람 한 번)
) {}
```

- 방마다 `Map<Long, TrendBox>`를 둔다. 틱 스레드만 읽고 쓰므로 락이 없다.

흐름
1. 생성: 방의 틱이 다음 생성 시각이 되면 작업 스레드에 키워드 선택을 맡긴다. 작업 스레드는 메모리의 나라별 키워드 캐시(활성 상태, 반영 소스가 외부, `trend_value` ≥ 상자 최소 트렌드 값, 금지 아님, 최근 상자 키워드 제외)에서 무작위로 하나 골라 큐로 돌려준다. 틱이 위치를 정해 상자를 추가한다. 위치는 벽에서 일정 거리 안쪽, 다른 상자와 일정 거리 이상, 가능하면 사람이 있는 구역 근처로 고른다.
2. 전송: 스냅샷에 상자 목록(`id`, 위치, 남은 시간, 내가 열었는지)만 싣는다. `keywordId`는 절대 스냅샷에 넣지 않는다. 관심 영역 밖 상자는 미니맵용으로 위치만 낮은 주기(예: 5초)로 보낸다.
3. 열기: 클라이언트가 WebSocket으로 `OPEN_BOX {boxId}`를 보낸다. 틱에서 순서대로 검사한다.
   - 상자가 있고 만료 전인가 → 없으면 `BOX_GONE`
   - 서버 기준 내 아이콘 위치와 상자의 거리가 기준(150px) 이하인가 → 아니면 `BOX_TOO_FAR`
   - 이미 열었는가 → 그러면 `BOX_ALREADY_OPENED`
   - 통과하면 `openedBy`에 추가하고, 그 연결에만 `BOX_OPENED {boxId, keywordId, text, trendValue, holderCount, revealUntil}`을 보낸다.
4. 공개: 클라이언트는 `revealUntil`까지 키워드 카드를 보여 주고, 지나면 이름을 가린다. 서버는 공개 시간 동안 그 사용자가 그 키워드를 상자에서 봤다는 것만 메모리에 둔다(검색하지 않고 카드에서 바로 `+`하는 요청을 허용하기 위해).
5. 소멸: 틱이 `expiresAt`이 지난 상자를 지우고 스냅샷에서 뺀다. 사라지기 30초 전부터 클라이언트가 깜빡임 효과를 그린다.

보안과 부하
- 키워드는 연 사람에게 개별 메시지로만 가므로 다른 사람의 네트워크 탭에도 보이지 않는다.
- 거리 검사는 클라이언트가 보낸 위치가 아니라 서버 시뮬레이션 위치로 한다.
- 키워드 선택은 메모리 캐시만 쓰고 DB를 조회하지 않는다. 상자 수가 방당 3개라 부하는 무시할 수 있다.
- 통계가 필요해지면(상자별 열람 수, 상자 키워드의 `+` 전환율) 상자 소멸 때 요약 한 줄을 로그로 남긴다. 지금은 테이블을 만들지 않는다.

### 9.9 키워드 카운터와 활동 기록 (4.12)
키워드마다 누적 카운터 세 개를 두고, 시간별 기록과 하루별 기록을 두 테이블로 나눠 남겨서 카드의 활동 그래프에 쓴다.

카운터 (`keywords`와 두 기록 테이블에 같은 이름으로 둔다)

| 컬럼 | 의미 | 바뀌는 때 |
|---|---|---|
| `search_count` | 누적 검색 수. 검색창에서 이 키워드로 찾아진 횟수(💤 포함, ⛔와 없는 단어 제외). 같은 사용자(방문자 ID)의 같은 키워드는 한 시간에 한 번만 센다 | 메모리 카운터를 매시 정각에 더함 |
| `total_plus_count` | 누적 `+` 수. 24시간 삭제·충돌로 잃어도 줄지 않는다(💤, 만료 행 재사용 포함) | `+` 트랜잭션에서 +1 |
| `holder_count` | 지금 가진 사람 수(`player_keywords` 행 수 캐시). 💤↔🔥 전환과 트렌드 값 계산(4.8)에 쓴다 | `+`(새 행) +1, 24시간 삭제·충돌로 잃기 −1 |

기록 테이블

| 테이블 | 한 행 | 보관 | 내용 |
|---|---|---|---|
| `keyword_log_24h` | 키워드 × 나라 현지 시 0~23의 24칸(`hour_slot`, 매시간 그 칸을 덮어쓴다. `recorded_at` = 그 시간의 시작, UTC 정시) | 키워드당 24칸(행을 지우지 않는다) | 그 시간이 끝날 때의 세 누적값(스냅샷)과 그 시간의 증분(`search_inc`, `total_plus_inc`, `holder_inc`, 음수 가능) |
| `keyword_log_dailies` | 키워드 × 하루(`recorded_at`의 나라 현지 날짜) | 180일 | 그날 마지막 누적값과 그날의 증분 합 |

흐름
1. 검색은 DB를 건드리지 않고 메모리 카운터만 올린다(키워드별 `LongAdder`). 중복 검사용 (사용자, 키워드) 집합도 메모리에 두고 시간이 바뀌면 비운다. `+`의 `total_plus_count`·`holder_count`는 9.4의 트랜잭션에서 바로 바꾼다.
2. 매시 정각 잡이 먼저 지난 한 시간의 검색 카운터를 비우며 `UPDATE keywords SET search_count = search_count + ?`로 더한다(키워드 번호 순으로 묶어서). 그다음 대상 키워드의 세 누적값과 그 시간의 증분을 `keyword_log_24h`에 `INSERT ... ON DUPLICATE KEY UPDATE`로 저장한다(그 시간의 칸을 덮어쓴다. 같은 시간을 다시 저장해도 같다). 증분은 그 시간 동안 메모리 카운터가 센 양이고, `holder_inc`는 직전에 저장한 가진 사람 수와의 차이다.
3. 같은 잡이 그 행이 속한 현지 날짜의 `keyword_log_dailies` 행을 그날의 시간별 행에서 다시 만든다(증분은 합, 누적값은 그날 마지막 행의 값). 날짜가 다른 옛 칸은 제외하고, 매번 덮어쓰므로 재시작해도 중복 합산이 없으며 별도의 자정 정리 작업이 필요 없다.
4. 대상은 그 시간에 카운터가 바뀌었거나 가진 사람이 1명 이상인 키워드다. 나머지는 행을 만들지 않고 그래프에서 증분 0으로 그린다.
5. 하루 행을 만든 직후 180일이 지난 `keyword_log_dailies` 행을 지운다. `keyword_log_24h`는 칸을 덮어쓰므로 지우지 않는다.
- 직전 기록이 없는 키워드(새 키워드, 기록을 시작하기 전부터 있던 키워드)는 그 시간 뒤에 `+`되어 아직 남아 있는 `player_keywords` 행 수를 그 시간의 `+`·가진 사람 증분으로 본다. 옛 누적값이 한 시간에 몰려 그래프가 튀지 않게 하려는 것이다.
- 대상의 "그 시간에 바뀐 키워드"는 `keywords.updated_at`이 그 시간 안인 행으로 찾는다(`+`, 24시간 삭제, 잃기, 상태 변경이 행을 고친다). 검색 수는 일괄 UPDATE라 `updated_at`이 바뀌지 않으므로 검색 카운터의 키워드 번호를 따로 넣는다.
6. `GET /api/keywords/{id}/activity`는 24시간이면 `keyword_log_24h`(조회할 때 `recorded_at`이 24시간이 지난 칸은 무시한다), 7일 이상이면 `keyword_log_dailies`의 증분을 읽고, 이번 시간·오늘은 `keywords` 누적값과 메모리 카운터를 합쳐 "집계 중"으로 만든다. 1분 캐시.
7. IN은 현지 날짜 경계가 정시가 아니라서 한 시간 행이 날짜를 가로지른다. `recorded_at`의 현지 날짜로 정하고 오차를 허용한다(IN 오픈 때 다시 확인).

부하 (가정: 시간당 활동 키워드 5,000개, 측정 전 추정)
- 매시 약 5,000행을 500행씩 묶은 다중 행 INSERT 10번으로 저장한다(JDBC `rewriteBatchedStatements=true`). `keyword_log_24h`는 활동이 있었던 키워드 수 × 24행이 상한(활동 키워드 5,000개면 약 12만 행)이고, `keyword_log_dailies`는 하루 최대 5,000행 × 180일 ≈ 90만 행이다.
- `+` 때 `keywords` 한 행을 고치므로 인기 키워드에 잠금이 몰릴 수 있다. 트랜잭션을 짧게 두고 잠금 순서(플레이어 → 키워드)를 지킨다.

주의
- 서버가 갑자기 꺼지면 그 시간의 검색 카운터(최대 1시간치)가 사라진다. 정상 종료 때는 저장한다. `+` 관련 카운터는 DB에 바로 쓰므로 사라지지 않는다. 서버가 여러 대가 되면 검색 카운터를 Redis로 옮긴다.
- 검색은 요청 빈도 제한(9.5) 안에서만 센다. 누가 검색했는지는 저장하지 않는다.
- 활동이 많은 키워드 목록이나 순위는 만들지 않는다(3.3).

## 10. 트렌드 데이터 수집

### 10.1 소스

| name(`data_sources.name`) | 소스 | 지역 | 내용 | 갱신 |
|---|---|---|---|---|
| google | 구글 트렌드 실시간 인기 RSS(`https://trends.google.com/trending/rss?geo=KR`) | KR | 지금 급상승 중인 검색어 상위 10개의 제목(`title`). 대략 검색량·관련 뉴스도 오지만 쓰지 않는다 | 약 10분(5분마다 조회) |
| admin | 관리자가 직접 넣은 키워드(13.7) | 전체 | 관리자 화면에서 넣은 🔥활성 키워드. 넣은 때부터 7일 유지 | - |
| internal | 서비스 내부 | 전체 | 외부에서 확인되지 않은 키워드의 반영 소스. 점수는 가진 사람 수만으로 정함(4.8) | - |

- 구글 실시간 인기는 공식 API가 아니라 구독용으로 공개된 RSS다. 인증·한도가 없고 `robots.txt`가 막지 않는 경로지만, 형식이 바뀌거나 막힐 수 있다. 그래서 읽지 못해도 게임은 그대로 돌고, 30분 넘게 읽지 못하면 목록을 모르는 것으로 보고 유지 기간 정리를 멈춘다(10.2). 정식 출시 전에 상업적 이용 가능 여부를 확인한다(16.2).
- 쓰지 않는 것: Google Ads 키워드 플래너, Google Trends API(알파), 구글 트렌드 웹 화면 스크래핑(내부 API `batchexecute` 포함), Custom Search JSON API, Search Console API, 네이버 데이터랩·검색 API, BigQuery Google Trends 공개 데이터셋, 위키백과, YouTube, 바이두, 웨이보, Yahoo! JAPAN.
- 나라별로 쓰는 소스는 위 표의 '지역' 열을 기준으로 코드(설정)에 둔다. `data_sources`에는 나라 열이 없고 소스마다 가중치 하나만 있어서, 같은 소스를 여러 나라가 쓰면 가중치를 공유한다.
- 해외 지역은 외부 소스를 정한 뒤 연다(16.1). 나라별로 독립 수집해서 한 소스가 막혀도 나머지는 계속된다.

### 10.2 수집 방식
- 구글 실시간 인기(google, KR)
  - 5분마다 RSS를 읽는다. 각 제목(`title`)에서 키워드에 쓸 수 없는 문자(`:` 같은 문장 부호)는 공백으로 바꾸고 4.6 등록 조건 검사를 거친다. 통과하지 못한 제목은 버린다.
  - 처음 올라온 제목은 키워드를 새로 만들어 🔥활성, 반영 소스 `google`로 시작한다. 이미 있는 키워드(금지 제외)는 반영 소스를 `google`로 바꾸고 🔥로 만든다. 이때부터 7일 동안 외부 데이터에 의한 활성으로 유지한다(`keywords.external_until` = 지금 + 7일, 설정값 `external_hold_hours` 기본 168). 목록에 다시 보일 때마다(5분마다) 지금 시각부터 7일로 다시 센다.
  - 유지 기간이 끝나면(5분마다 검사) 지금 목록에 아직 있으면 지금부터 다시 7일 유지한다. 목록에 없으면 반영 소스를 `internal`(내부 데이터)로 바꾸고, 가진 사람이 활성 조건(3명) 이상이면 🔥를 유지, 미만이면 💤비활성으로 바꾼다. 트렌드 값과 보유자 점수도 다시 계산한다.
  - 사용자가 검색한 단어가 지금 목록에 있으면 외부 확인으로 보고 같은 방식(반영 소스 `google`, 7일 유지)으로 등록한다.
  - RSS를 30분 넘게 읽지 못하면 지금 목록을 모르는 것이므로 유지 기간 정리를 미룬다(일시 장애로 키워드가 한꺼번에 💤가 되지 않게).
- 소스 값(0~100)은 "외부 확인" 판정에만 쓰고 저장하지 않는다. 값이 10 이상이면 외부 확인이다(`settings`의 외부 확인 관심도). 구글 실시간 인기는 지금 목록에 있으면 100, 없으면 값 없음이다.
- 수집한 검색어는 소스 코드로 소스를 정하고, 나라와 정규화 키(`normalized`, 4.18)로 키워드를 찾는다. 사용자가 검색한 새 단어에서 외부 확인이 되어 등록될 때는(4.4) 그 값을 준 소스가 반영 소스가 된다.
  - 저장 전에 4.6의 등록 조건과 같은 검사(20자 이하, 허용 문자, 개인정보 패턴, 차단어·민감 키워드, 실존 인물 이름 패턴)를 거치고, 통과하지 못한 검색어는 버린다. 키워드가 되어도 목록으로 보여 주지 않는다(3.3).

### 10.3 반영 소스와 가중치

```
외부 확인 = 그 나라의 외부 소스 값 ≥ 10
반영 소스 = 외부 확인이 되면 그 외부 소스, 아니면 반영 중이던 외부 소스(값이 있는 한) 또는 internal
```

| 나라 | 소스 | 가중치(반영값, 0~2) |
|---|---|---|
| KR | google / admin / internal | 1 / 1 / 1 |
| GLOBAL·JP·IN | internal(외부 소스 미정, 16.1) | 1 |

- 나라마다 외부 소스는 하나이고, 한 키워드는 한 번에 반영 소스를 하나만 가진다(`internal` 아니면 그 외부 소스). 외부 소스를 더 붙이면 외부 확인된 소스 중 가중치가 큰 쪽을 쓴다. 트렌드 값의 식은 4.8에 있다.
- 가중치는 소스마다 하나이고 0~2다(식의 최대 기준값 50 × 최대 가중치 2 = 100 상한 때문). 0이면 그 소스를 반영 소스로 쓰지 않는다(외부 확인에서도 제외). 지금은 모든 소스가 1이다(125판).
- 위 표는 `data_sources`의 초기값이다. `internal`은 모든 나라가 같은 행을 쓴다. 기본 비율(10), 최대 기준값(50), 로그 완만도(30), 기준 인원(1,000), 외부 확인 관심도(10)는 `settings`에 둔다(13.3).

## 11. 데이터 모델

### 11.1 규칙
- 테이블 이름은 소문자 스네이크 케이스 복수형(마지막 단어만, 기간 접미사 `24h`는 복수형을 쓰지 않는다). 다대다 매핑은 `앞 단수_뒤 복수`(`player_keywords`). 외래 키 컬럼은 `참조 단수_id`이고(복수형의 s를 뗀다) 기본 키 바로 다음에 순서대로 둔다(ERD Cloud에 보이는 순서). 한 테이블에서 같은 테이블을 두 번 참조하는 컬럼은 두지 않는다(그래서 공지·가이드에는 마지막 수정 관리자 컬럼이 없다).
- 모든 테이블의 기본 키는 `id BIGINT AUTO_INCREMENT` 하나다. 관계는 모두 비식별, 복합 키 없음. "한 번만" 규칙은 `UNIQUE`로 지킨다.
- 시각 컬럼: 기본은 `created_at`·`updated_at`(`DATETIME(6)`, UTC, DB 기본값 + JPA Auditing). 고치지 않는 기록 테이블(`*_histories`, `user_notices`, `blocked_terms`, `chat_messages`)은 `created_at`만이다. 칸을 덮어쓰거나 매시간 다시 쓰는 `player_score_24h`, `player_score_dailies`, `keyword_log_24h`, `keyword_log_dailies`는 `updated_at`도 둔다. 업무 시각(`expires_at`, `banned_at` 등)은 따로 둔다.
- 정수 컬럼은 확장에 대비해 모두 `BIGINT`로 둔다(점수, 인원, 횟수, 순서, 레벨 등). 참/거짓은 `TINYINT(1)`, 0~23 시간 칸 같은 작은 번호는 `TINYINT`로 둔다. JPA 엔티티 필드도 `long`이다.
- 코드값은 `ENUM` 대신 `VARCHAR` 대문자 코드(`countries`, `users.role` 등) 또는 이름만 있는 코드 테이블(`user_states`, `keyword_states`, `report_types`, `sanction_types`)로 둔다. 허용 값 검증은 `CHECK` 없이 애플리케이션에서 한다.
- 같은 단어 판정 컬럼(`keywords.normalized`, 차단어)은 `utf8mb4_bin`. 정규화는 애플리케이션에서 한다.
- JPA 엔티티는 단수형 클래스 + `@Table(name = "users")`. 개발·테스트에서는 시작할 때 JPA가 스키마를 반영하고 시더가 초기 데이터를 넣는다(11.3). 운영은 17절 SQL(`4-schema.sql`)을 기준으로 Flyway를 쓴다.

### 11.2 테이블 (32개, 컬럼은 `4-schema.sql`)

| 분류 | 테이블 | 역할 |
|---|---|---|
| 사용자 | `user_states` | 사용자 상태(정상 / 탈퇴) |
| | `users` | 계정(게스트 포함). 방문자 ID, OAuth 종류·ID, 상태, 권한, UI 언어, 계정 금지·업로드 정지 만료 시각, 탈퇴 시각 |
| | `players` | 플레이어 = 사용자 × 나라. 닉네임, 누적 검색 횟수, EXP·레벨, 활성 칭호, 현재 유행도(실시간 점수), 보유 키워드 수, 누적 `+` 횟수, 댓글 점수 |
| | `profiles` | 플레이어별 꾸미기 프로필(성장형·픽셀 그림·업로드 이미지, 바탕·테두리 색과 두께). 한 플레이어에 여러 개, 하나만 장착 |
| | `titles`, `player_titles` | 칭호 목록과 플레이어별 보유 칭호 |
| | `levels` | 레벨별 필요 누적 EXP(1~100레벨, 관리자가 수정) |
| | `refresh_tokens` | 계정 로그인 세션(갱신 토큰 해시, 만료·폐기) |
| 키워드 | `countries` | 나라 설정(시간대, 오픈 여부, 방 상한) |
| | `keyword_states` | 키워드 상태(활성 / 비활성 / 금지) |
| | `keywords` | 나라별 키워드. 표시용·정규화 단어, 상태, 반영 소스, 트렌드 값, 누적 검색 수·`+` 수, 가진 사람 수 |
| | `player_keywords` | 보관함. `+` 한 번 = 한 행, 24시간 뒤 삭제 |
| | `keyword_log_24h`, `keyword_log_dailies` | 키워드 활동 기록. 시간별(키워드당 24칸 덮어쓰기)과 현지 하루별(180일), 누적 스냅샷과 증분 |
| | `data_sources` | 데이터 소스별 반영값(가중치) |
| 소셜 | `comments`, `comment_votes` | 키워드 댓글과 투표 |
| | `chat_messages` | 채팅(30일 보관) |
| | `report_types`, `reports` | 신고 대상 유형과 신고(대상 유형 + ID) |
| 운영 | `notices`, `user_notices` | 공지와 팝업 열람 기록 |
| | `guide_categories`, `guides` | 게임 가이드 |
| | `player_score_24h`, `player_score_dailies` | 점수 추이 로그(플레이어 1 : 기록 N). 시간별은 플레이어당 24칸 덮어쓰기, 하루별은 영구 |
| | `settings`, `setting_histories` | 밸런스 설정과 변경 이력 |
| | `blocked_terms`, `blocked_nicknames` | 차단어, 금지 닉네임 |
| | `sanction_types`, `sanctions` | 제재 타입(타입별 제재 일수)과 사용자 제재 기록(사유·기간·해제) |

- `player_keywords.player_id`의 나라와 키워드의 나라가 같은지, `chat_messages.country_id`와 `player_id`의 나라가 같은지, 댓글·투표의 `player_id`가 그 댓글 키워드의 나라인지는 애플리케이션에서 검사한다. `keywords.data_source_id`(반영 소스)는 항상 값이 있고, 외부에서 확인되지 않은 키워드(사용자가 만든 것, 관리자가 넣은 시드 포함)는 `internal`을 가리킨다. `reports.target_id`는 대상이 여러 테이블이라 외래 키가 없다.
- 계속 쌓이는 테이블은 `keyword_log_dailies`(180일)와 `player_score_dailies`(영구)다. 커지면 월 파티션으로 나누고 기본 키를 각각 `(id, record_date)`로 바꾼다. `player_score_24h`는 플레이어당, `keyword_log_24h`는 키워드당 최대 24칸을 덮어써서 항상 24시간치만 남는다. `player_keywords`는 하루치만 남는다.

### 11.3 스키마 자동 반영과 초기 데이터 (개발·테스트)
스키마
- 애플리케이션이 시작할 때마다 JPA(Hibernate)가 엔티티를 보고 DB 스키마를 맞춘다(`spring.jpa.hibernate.ddl-auto=update`). 엔티티에 테이블·컬럼·인덱스를 추가하면 다음 실행에 반영된다. 이 환경에서는 Flyway를 끈다.
- `update`는 새 테이블·컬럼·인덱스를 추가하지만 컬럼 이름 변경, 타입 변경, 컬럼·테이블 삭제는 하지 않는다. 그런 변경을 했거나 스키마가 꼬였을 때는 아래 빠른 초기화를 쓴다.
- 엔티티가 만드는 스키마가 17절(`4-schema.sql`)과 같아지도록 `columnDefinition`, `@Table(indexes, uniqueConstraints)`, `@Comment`로 맞춘다. JPA로 표현하기 어려운 것(`utf8mb4_bin` 콜레이션, `DEFAULT CURRENT_TIMESTAMP(6)`, `guides`의 ngram FULLTEXT 인덱스)은 `columnDefinition`이나 시작 시 보조 SQL로 처리한다(없을 때만 만든다).

빠른 초기화
- `reset` 프로필(또는 환경 변수 `TRENDIST_DB_RESET=true`)로 실행하면 스키마의 모든 테이블을 지우고 엔티티로 다시 만든 뒤 초기 데이터를 넣는다. 엔티티에 없는 옛 테이블까지 지우려고 `information_schema`의 테이블 목록을 읽어 외래 키 검사를 끄고 지운다. Hibernate가 스키마를 만들기 전에 실행되는 초기화 빈이 맡는다.
- 안전장치: DB 주소가 로컬(localhost 등)이고 `reset` 프로필일 때만 동작한다. 운영 프로필과 같이 켜져 있으면 시작을 거부한다. 지우기 전에 대상 스키마 이름을 로그에 남긴다.

초기 데이터
- 시작할 때마다 시더(`ApplicationRunner`)가 스키마 반영이 끝난 뒤 필요한 테이블을 검사해서, 비어 있으면 초기값을 자동으로 넣는다. 그래서 `reset` 직후나 새 DB에서도 바로 테스트할 수 있다.
- 코드가 이름으로 찾는 테이블(`user_states`, `keyword_states`, `data_sources`, `settings`, `report_types`, `sanction_types`, `countries`, `levels`)은 없는 행만 채운다(고유 이름·번호 기준, 기존 값은 덮어쓰지 않는다). 코드를 고쳐 새 설정이나 상태가 늘어도 다음 실행에 채워진다.
- 운영에서 지우거나 고치는 데이터(`titles`, `guide_categories`, `blocked_nicknames`, `blocked_terms`)는 테이블이 비어 있을 때만 넣는다. 관리자가 일부러 지운 행이 다시 생기지 않게 하려는 것이다.
- ID 순서가 번역 키가 되는 테이블(`titles`, `keyword_states`, `user_states`)은 시드 순서를 고정해서 `reset`을 해도 같은 ID가 나오게 한다.

| 테이블 | 넣는 값 |
|---|---|
| `countries` | KR(오픈), GLOBAL·JP·IN·CN(닫힘), 시간대·기본 언어·방 상한 |
| `settings` | 13.3의 기본값과 허용 범위 |
| `data_sources` | google 1, admin 1, internal 1 (10.3). 예전 DB에 남은 naver_datalab·bq_trends_rising 행은 지우고(그 소스를 쓰던 키워드는 internal로, 3명 미만이면 💤), 예전 기본값 2 그대로인 반영값은 1로 바꾼 뒤 트렌드 값과 점수를 다시 계산한다 |
| `levels` | 1~100레벨 필요 누적 EXP (4.9의 곡선으로 계산) |
| `user_states`, `keyword_states` | 정상 / 탈퇴, 활성 / 비활성 / 금지 |
| `titles` | 4.10의 22개를 번호 순서대로 |
| `report_types`, `sanction_types` | 키워드 / 댓글 / 아이콘 이미지 / 플레이어 / 채팅, 계정 금지 / 이미지 업로드 정지 / 댓글 작성 정지 (13.5) |
| `guide_categories`, `guides` | 4.17의 초기 카테고리 6개와 놀이 방법 글(각 테이블이 비어 있을 때만) |
| `users` | 공지·가이드 작성자로 쓰는 운영자 계정(`role` = ADMIN, 로그인 수단 없음) 1개, 없을 때만 |
| `blocked_nicknames`, `blocked_terms` | 기본 금지어 소수(비어 있어도 동작) |

## 12. 빌드와 배포
- Gradle Wrapper, Kotlin DSL, Java Toolchain 25.

```kotlin
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
```

- Spring Boot는 4.1.2(2026-10-26 확인, 최신 안정판)로 고정한다. 4.2.0-RC1은 미리보기라 쓰지 않는다. 의존성은 범위 지정 없이 정확한 버전으로 적는다.
- 버전 구성(2026-10-26 확인)
  - Java: JDK 25 LTS. Oracle JDK는 25.0.5(2026-10 보안 패치)가 최신이다. Spring Boot 4.1.2는 Java 17 이상이고 27까지 지원한다.
  - Spring Framework: 7.x. Spring Boot 4.1.2는 7.0.10 이상을 요구한다.
  - Gradle: 9.8.2. Java 25는 Gradle 9.1.0부터 지원하고, Spring Boot 4.1.2는 Gradle 8.14 이상 또는 9.x를 지원한다.
  - 내장 서버: Tomcat 11.0.x(Servlet 6.1).
  - Spring Boot 4는 Jackson 3(`tools.jackson`), Hibernate 7.x, Spring Security 7.x를 쓴다. 4.1.0 기준 Hibernate 7.4, Spring Security 7.1.0, Flyway 12.4.0이고, 4.1.2가 관리하는 정확한 버전은 BOM을 따른다.
- 주요 의존성: spring-boot-starter-webmvc(예전 이름 starter-web은 폐기 예정), starter-websocket, starter-data-jpa, starter-security, starter-validation, starter-flyway와 flyway-mysql(운영용, 개발·테스트에서는 끈다), MySQL JDBC, ShedLock, Caffeine, Google API Client, ONNX Runtime, Testcontainers(MySQL).
- `spring.threads.virtual.enabled=true`(게임 틱 스레드는 플랫폼 스레드).
- `./gradlew bootJar` → Docker 이미지. 프론트엔드는 따로 빌드해 Nginx로 제공한다.
- 로컬 개발: 브라우저는 http://localhost:8080 하나로 접속한다. 프론트 개발 서버(Vite)가 8080에서 화면을 주고 `/api`·`/ws`를 백엔드(8081)로 넘긴다(운영의 Nginx 역할). 로컬 DB는 같은 PC의 MariaDB(DB `trendist`)이고, DB 계정은 git에 올리지 않는 `src/main/resources/application.env`에 둔다.
- 설정은 `application.yml`(공통), `application-dev.yml`(로컬, 기본 프로필), `application-prod.yml`(운영)로 나누고, DB 연결은 환경 변수(`DEV_MYSQL_URL`·`PROD_MYSQL_URL`·`MYSQL_USERNAME`·`MYSQL_PASSWORD`, 운영 포트 `PROD_SERVER_PORT`)로 받는다. 운영은 Flyway 전환 전까지 `ddl-auto=update`다(16.1).
- 백엔드 배포: `main`에 push하면 GitHub Actions가 테스트·`bootJar` 뒤 Docker 이미지(`dlrudgjs104/trendist-back:latest`)를 Docker Hub에 올리고, 서버에서 기존 컨테이너를 내린 뒤 새로 띄운다(단일 컨테이너 중단 배포). 운영 환경 변수는 GitHub 시크릿 `ENV_FILE`을 배포 때 서버 `/home/ubuntu/trendist/.env`로 보내고(WebSocket 허용 주소 `PROD_ALLOWED_ORIGINS`, 기본 `https://trendist.kr`), 실행 설정(포트 4001, 네트워크)은 서버의 docker compose 파일이 맡고, nginx는 `trendist-back:4001`로 넘긴다.
- 저장소: 프론트엔드와 백엔드를 GitHub 저장소 두 개로 따로 관리한다(`Trendist-Project/trendist-front`, `Trendist-Project/trendist-back`).
- 기획·기술·운영·SQL 문서는 백엔드 저장소의 `docs/`에서만 관리한다.
