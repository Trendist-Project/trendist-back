# Trendist 2부. 기술

> 문서 목록과 변경 이력은 [docs/README.md](README.md). 절 번호(1~17)는 네 문서에 걸쳐 이어지고, 코드 주석의 `(4.8)` 같은 번호가 이 번호다.

## 7. 기술 스택

| 영역 | 선택 |
|---|---|
| 프론트엔드 | React + TypeScript, Vite. 아레나는 Canvas 2D(부족하면 PixiJS) |
| 백엔드 | Spring Boot 4.1.2, Java 25, Gradle 9.8.2(Kotlin DSL). REST API, WebSocket 게임 서버, 배치를 한 애플리케이션으로 시작 |
| 실시간 통신 | Spring WebSocket(순수 WebSocket, 바이너리 프레임), 서버 권위. 20Hz 시뮬레이션, 10~15Hz 스냅샷. STOMP 미사용 |
| DB | MySQL 8, InnoDB, `utf8mb4`, UTC 저장. 스키마는 Flyway가 바꾼다(로컬 개발·운영, 11.3). 자동 테스트는 H2에 JPA가 스키마를 만든다. 로컬 개발은 우분투의 MariaDB 10.11(DB `trendist`), 자동 테스트는 H2 메모리 DB |
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
  - 맞출 때는 예측 위치만 서버 위치로 옮기고, 화면 위치(카메라)는 순간 이동 없이 약 0.12초에 걸쳐 따라간다. 다만 둘 다 멈춰 있을 때 생긴 작은 차이(12px 이하)는 화면에 바로 보여 주지 않고, 다음에 움직이기 시작할 때 섞어서 따라간다(멈춘 원이 혼자 한 번 움직여 보이지 않게). 서버 쪽 원이 움직이는 중(튕김 등)이거나 12px보다 크게 어긋나면 멈춰 있어도 바로 따라간다. 1,500px 넘게 어긋나면(입장·순간 이동) 바로 맞춘다(아래 모바일 끊김 방지).
- 모바일 끊김 방지
  - 다른 원은 서버 시각(스냅샷의 틱 번호 × 50ms)으로 140ms 늦게, 앞뒤 스냅샷 사이를 보간해 그린다. 도착 간격이 들쭉날쭉해도 멈췄다 튀지 않는다. 서버 시각과 브라우저 시각의 차이는 가장 빨리 도착한 스냅샷 기준으로 잡는다.
  - 내 원의 예측은 서버 틱처럼 50ms씩 나눠 진행하고, 프레임이 늦게 와도 그 시간을 버리지 않는다(최대 1초). 시간을 버리면 서버보다 뒤처져 보정으로 화면이 튄다.
  - 1,500px 넘게 어긋날 때만 바로 맞추고, 그보다 작으면 화면을 부드럽게 따라가게 한다(튕김·지연으로 수백 px 어긋날 때 순간 이동하지 않게).
  - 캔버스 크기는 정수로 맞추고 DPR은 2까지만 쓴다(DPR이 소수인 휴대폰에서 매 프레임 캔버스를 새로 만들지 않게). 원 그림자는 `shadowBlur` 대신 반투명 원, ? 구슬은 한 번 그린 그림을 찍는다. 그리다 오류가 나도 다음 프레임은 계속 돈다.
- 바닥 격자는 작은 타일을 한 번 그려 반복한다. 화면 밖 아이콘은 그리지 않고 이미지는 한 번 받아 캐시한다.
- 서버 데이터는 TanStack Query, 전역 UI 상태는 Zustand(후보, 버전 고정).
- 다국어: 외부 라이브러리 없이 작은 자체 i18n을 `src/i18n`에 둔다. 네임스페이스마다 `ko/`·`en/` 파일을 따로 두고(예: `src/i18n/ko/errors.ts`, `src/i18n/en/errors.ts`), 빠진 영어는 한국어로 대체한다. 나라는 `src/country.ts`가 정한다(KR=ko, GLOBAL=en, localStorage `trendist.country`, 바꾸면 페이지 새로 불러옴). 모든 API 요청에 `Accept-Language: ko|en`을 싣는다. 관리자 화면은 한국어로 고정한다. 서버 오류 메시지는 한국어 그대로이고, 화면이 오류 코드로 번역한다(`src/i18n/{ko,en}/errors.ts`). DB의 이름(칭호, 가이드, 공지, 효과 등)은 한국어 칸과 영어 칸(`*_en`)을 함께 두고 서버가 `Accept-Language`에 맞춰 내려 준다(9.6, 11.2). 키워드 상태·사용자 상태·나라 이름은 한글 하나만 두고 코드·ID로 번역한다(ID는 초기 데이터 순서로 정해지므로 순서를 바꾸거나 지우지 않는다. 안 쓰는 칭호는 `enabled`를 끈다).
- 날짜·숫자는 `Intl` API, 폰트는 Noto Sans(KR·JP·SC·Devanagari)를 필요한 것만 불러온다. 가이드 본문의 그림 토큰은 한국어 토큰이 기준이고 영어 별칭(`[[controls]]` 등)도 받는다(4.17).
- 키워드 활동 그래프(4.12): SVG로 직접 그리거나 가벼운 차트 라이브러리(후보 uPlot·Recharts, 버전 고정)를 쓴다. 응답은 TanStack Query로 1분 캐시한다.
- 성공 알림(토스트): 저장·수정·삭제·복사·신고처럼 요청이 잘 끝나면 화면 위 가운데에 초록 알림을 약 2.4초 띄운다(창 위에 겹쳐 보임, 최대 3개, 같은 문구는 하나만). 어느 화면에서든 `successToast('저장했어요')`를 부르고, 알림 영역(`<Toaster />`)은 게임·관리자 페이지에 하나씩 둔다. 오류는 그 입력 칸·창 안에 보여 준다(무엇을 고쳐야 하는지 그 자리에서 보이게). 아레나의 게임 알림(키워드 잃기·빼앗기 등)은 따로다.
- 확인 토스트(2026-11-05): 브라우저 기본 확인 창(`window.confirm`)은 쓰지 않는다. `await confirmToast('…할까요?')`(관리자 화면은 `adminConfirm`)이 화면 위 가운데에 노란 토스트로 질문과 **[예]·[아니오]** 버튼을 띄우고 고를 때까지 남아 있다(Esc = 아니오, 새 확인이 뜨면 이전 것은 아니오, 한 번에 하나). 예를 누르면 true. 저장하지 않은 채 프로필 칸·화면을 옮길 때, 꾸미기 [취소], 댓글 삭제, 관리자 제재·권한·닉네임·꾸미기 초기화·강제 퇴장·신고 처리·가이드 편집 버리기가 이것을 쓴다. 링크 주소 입력(`window.prompt`, 관리자 글 편집기)은 입력 칸이 필요해 그대로다.
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
- 충돌은 공간 격자로 가까운 아이콘끼리만 검사한다. 격자 칸 크기는 일반 크기 기준이고, 큰 아이콘(반지름 300px까지)은 걸치는 칸 모두에 등록한다.
- 충돌로 키워드 잃기(4.3): 틱에서 충돌을 찾으면 두 사람의 현재 유행도를 메모리 값으로 비교하고, 보호 조건(잃은 뒤 10초, 입장 직후 10초, 봇)을 사용자별 보호 종료 시각 하나로 검사한다. 통과하면 "키워드 잃기" 이벤트만 큐에 넣고 틱은 계속 돈다. 실제 처리는 9.4를 따른다.
- 나가기: 페이지를 떠날 때(구글 로그인으로 이동, 새로고침, 탭 닫기, 로그아웃) 클라이언트가 `bye`를 보내 바로 방에서 빠진다. 네트워크가 끊겼는데 닫힘 신호가 오지 않거나 중간 프록시가 서버 쪽 연결을 남겨 두는 경우를 위해, 90초 동안 아무 메시지(2초마다 보내는 ping 포함)도 없는 접속은 방에서 빼고 연결을 닫는다(백그라운드 탭은 브라우저가 타이머를 1분 간격까지 늦춘다). 그러지 않으면 랭킹·방에 유령 접속자가 남는다.
- 느린 클라이언트는 송신 버퍼 상한을 넘으면 오래된 스냅샷을 버린다. DB·외부 호출은 틱 스레드 밖에서 가상 스레드로 처리한다.
- 방 관리자와 입장 라우팅은 인터페이스로 분리해 두고, 나중에 지역당 여러 방·여러 서버로 늘린다.
- 설정값 전달: 이동 계산은 항상 서버가 `settings`(1분 캐시)의 값으로 한다. 클라이언트가 예측에 쓰는 값(최대 속도, 반지름·줌 곡선 값)은 입장할 때 서버가 보내 주고, 관리자가 바꿔서 설정이 갱신되면 방 안 모든 클라이언트에 다시 보낸다. 그러지 않으면 내 아이콘 예측이 서버 위치와 어긋난다.
- 위치·활동 영역 저장: 위치는 방 메모리(`members`, 나간 사람은 `lastSeen`)에만 있어서 서버를 다시 켜면 사라졌다. 그래서 30초마다(`RoomManagerImpl`의 스케줄) 그리고 서버를 끌 때(`@PreDestroy`, 웹소켓이 먼저 닫혀도 `lastSeen`에 남은 위치까지 포함) `Room.snapshotPositions()`를 모아 `RoomStateService.save`가 `player_positions`(플레이어당 한 행, 위치와 저장 시각)와 `room_states`(나라당 한 행, 활동 영역)에 덮어쓴다. 저장은 틱 스레드에서 값만 모으고 DB는 작업 스레드에서 쓰며, 하루가 지난 위치는 지운다. 입장할 때 방이 기억하는 위치(`lastSeen`, 5분)가 있으면 그것을, 없으면 DB의 위치를 `Room.Resume`으로 받아 그 자리에서 시작한다(저장한 지 5분(`REJOIN_KEEP_SECONDS`)이 지났으면 쓰지 않고, 활동 영역 밖이면 영역 안으로 맞추며, 서버를 다시 켠 직후라 입장 보호를 준다). 나간 사람 위치는 나간 뒤 지난 시간만큼 전의 것으로 저장해 5분이 늘어나지 않게 한다.
- 활동 영역 서서히 줄이기: 한 변의 목표는 `기준 맵 × √(인원/1000)`(최소 기준 맵의 1/4)이고, `Room`이 지금 영역(`areaNow`)을 따로 들고 있다가 목표가 크면 바로 커지고 작으면 설정 `area_shrink_speed`(px/s, 기본 60, 0이면 바로)만큼씩 줄인다. 방을 만들 때 저장해 둔 영역(5분 안)에서 시작해, 서버를 다시 켠 직후 몇 명만 돌아와도 영역이 좁아져 모두가 가장자리로 밀리는 일이 없다. 구슬·세이프존 개수와 이동 범위(`clamp`)는 이 지금 영역을 쓴다.
- 관리자 관전(13.13): 클라이언트가 `auth` 대신 `spectate{token, country}`를 보내면 관리자 토큰(`AccountGuard.isAdmin`)인지 확인하고 `Room.addSpectator`로 받는다. 관전자는 `members`에 넣지 않아 방 크기·활동 영역·충돌·봇 수·랭킹(`liveView`)·접속 인원에 영향이 없고, 방이 모두에게 보내는 스냅샷(`me` 표시 0)·미니맵·`roster`·`orbs`·`orbSpawn`·`orbEaten`·`safeZones`·`chat`·`emote`·`combo`·`dashFx`·`shield`·`tuning`만 받는다. 입장할 때 `roster`·`orbs`·`safeZones`·`chatHistory`를 받고 서버는 `spectating{country, spectators}`로 알린다. 받은 입력(`input`·`move`·`chat`·`emote`)은 모두 버리고 `ping`·`bye`만 받는다. 90초 동안 신호가 없으면 정리하고(`dropSilent`), 방마다 10명까지다. 관리자가 아니거나 토큰이 틀리면 `1008 AUTH_FAILED`로 닫고, 가득 차면 `1008 SPECTATORS_FULL`이다.
- 이모트(4.26): 클라이언트가 `emote{code}`를 보내면 정해진 코드인지와 빈도(9.5)만 보고 방 전체에 `emote{e, code}`를 보낸다.
- 대시(4.27): 클라이언트가 `dash`를 보내면 방(`Room.dash`)이 쿨타임(`Member.dashReadyAt`)과 움직이는 중인지를 보고, 되면 `dashUntil`까지 `advance`의 최대 속도에 `Room.DashTuning.multiplier`를 곱하고 본인에게 `dash{ms, cooldownMs, mult}`를 보낸다. 클라이언트 예측(`Arena.startDash`)은 같은 배수로 가고, 보정 허용 오차도 배수를 곱한다. 설정은 1초마다 다시 읽는다(`RoomManagerImpl.dashTuning`).
- 긴급 안내(4.29): 관리자가 올리거나 끝내면 `EmergencyEvents.Changed`를 내보내고, 커밋된 뒤 `RoomManagerImpl`이 모든 나라 방의 큐에 `Room.announce`를 넣어 접속 중인 모두(관전 포함)에게 `{type: "emergency", notice: {…}|null}`를 보낸다(notice가 null이면 걷는다). 서버를 다시 켜도 DB에서 읽으므로 진행 중인 안내가 이어진다.
- 연속 빼앗기 콤보(4.25): 작업 스레드가 빼앗기·잃기를 끝내면 방 큐에 결과를 넣고, 틱 스레드가 이긴 사람의 콤보 +1, 잃은 사람의 콤보 0을 하고 `combo{e, nickname, combo, event, flameAt, by, byE}`를 방 전체에 보낸다. 불꽃은 클라이언트가 `combo >= flameAt`일 때 그린다(명단 `roster`에도 `flameAt`). 설정은 1초마다 다시 읽는다(`Room.ComboTuning`). 불꽃인 사람이 불꽃 없는 사람과 부딪히면 방이 `Outbox.requestWipe`로 작업 스레드에 넘기고(`KeywordService.loseAll`), 끝나면 방 큐에서 `fightResult`와 `dropPrizeOrbs`로 ! 구슬을 떨어뜨린다. ! 구슬은 `orbSpawn{prize:true}`로 알리고 `orbs` 목록 항목의 마지막 값이 1이다.
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
- WebSocket: 토큰을 연결 주소의 쿼리에 담지 않고(로그에 남는다) 연결 직후 첫 메시지로 액세스 토큰을 보낸다. 첫 메시지에는 화면 언어 `lang`(ko/en)도 실어 서버가 그 연결의 `chatError` 같은 메시지를 그 언어로 보낸다(채팅 금지 `CHAT_BANNED`에는 끝나는 시각 `until`(epoch ms, 0은 영구)이 있다). 5초 안에 인증하지 못하면 끊는다. 이미 연결된 뒤에 토큰이 만료되어도 그 연결은 유지하고, 다시 접속할 때 새 토큰을 쓴다.
- 갱신 요청은 `POST`이고 `SameSite=Lax` 쿠키라 다른 사이트에서 보낼 수 없다. CORS는 우리 도메인에만 허용한다. 방문자 ID와 토큰은 URL·쿼리·로그에 넣지 않는다.
- OAuth 제공자의 사용자 ID(구글은 `sub`)는 SHA-256 해시로만 저장하고, 제공자 종류와 함께 `oauth_type`·`oauth_id`에 둔다. 금지된 계정(`users.banned_until`이 지금보다 뒤)은 입장과 WebSocket 연결을 막는다. 관리자 API는 `ROLE_ADMIN`을 서버에서 검사한다.

### 9.4 `+` 처리와 점수 재합산
`+` 처리
- 한 트랜잭션: `players` 행을 `SELECT ... FOR UPDATE`로 먼저 잠그고, `player_keywords`에 INSERT(또는 만료 행 재사용, 아래)한다(`expires_at` = 지금 + 24시간). 그다음 `keywords` 행의 `holder_count`·`total_plus_count`를 고치고 `trend_value`를 다시 계산한다. 마지막으로 `players.exp` +1(레벨은 메모리에 올려 둔 `levels` 표로 다시 정해 `players.level_id`에 그 행을 넣음), `players.current_score`에 그 키워드의 새 점수(`trend_value`)와 늘어난 EXP 점수(100 미만일 때 1)를 더하고, `total_plus_count` +1, 새 행이면 `hold_count` +1 한다. 잠금 순서(플레이어 → 키워드)는 항상 같게 둔다. 접속 중이면 방 메모리 점수도 같이 고친다.
- 같은 키워드 중복은 `UNIQUE(player_id, keyword_id)`가 막는다. 화면의 `+` 버튼은 비활성화되어 있고, 서버는 만료 전 행이 있으면 요청을 거부한다.
- 만료 행 재사용: `expires_at`이 지났지만 1분 잡이 아직 지우지 않은 행이 있으면 INSERT하지 않고 그 행을 UPDATE한다(`created_at`과 `expires_at`을 새 `+` 값으로 바꾼다). 행 수가 그대로이므로 `keywords.holder_count`와 `players.hold_count`는 바꾸지 않고, 두 `total_plus_count`만 +1 하며, `current_score`는 그 사람의 목록을 다시 합산해서 구한다. `created_at`을 갱신하므로 이 엔티티는 JPA Auditing의 `updatable = false`를 쓰지 않는다.
- 1분 주기 잡이 `expires_at`이 지난 행을 삭제하고 `keywords.holder_count`와 `players.hold_count`를 −1(0 아래로 내려가지 않게), `trend_value`를 다시 계산한 뒤 그 플레이어의 점수를 다시 합산한다. 같은 `players` 행을 잠그고 `DELETE ... WHERE id = ? AND expires_at <= 지금`처럼 조건부로 지워서, 방금 재사용된 행을 지우거나 두 번 처리하지 않는다. 조회할 때도 `expires_at`을 검사한다.
- 충돌로 잃기: 틱 밖의 작업 스레드가 잃는 사람의 후보 행(키워드 미금지) 중 하나를 무작위로 골라 DELETE하고, 같은 트랜잭션에서 `holder_count`·`hold_count`를 −1, `trend_value` 재계산, 그 플레이어의 점수 재합산을 한다. 행이 지워지므로 바로 다시 `+`할 수 있다. 끝나면 방에 점수 변경을, 잃은 사람에게만 알림을 보낸다. 후보가 없으면(`hold_count`가 0이면 조회 없이) 아무것도 하지 않는다.
- 충돌로 빼앗기(사람끼리): 위 잃기와 같은 트랜잭션에서 이긴 사람에게 그 키워드를 준다. 두 `players` 행을 번호 순서로 먼저 잠그고(서로 동시에 빼앗아도 교착하지 않게) 키워드 행은 그다음에 잠근다. 이긴 사람에게 만료 전 행이 있으면 `created_at`·`expires_at`만 지금 기준으로 갱신하고, 없으면 `+` 처리와 같이 넣는다(만료 행 재사용 포함). 잃은 사람 −1, 이긴 사람 +1이라 `holder_count`와 트렌드 값은 결국 그대로다.

트렌드 값과 점수 갱신
- 트렌드 값 재계산: 한 키워드의 `trend_value`는 4.8의 식으로 `holder_count`, 그 나라 활동 인원, 반영 소스(`data_source_id`)의 가중치, 상태에서 구한다. 활동 인원(만료 전 키워드를 가진 플레이어 수, 탈퇴 제외)은 메모리에 두고 1분마다 다시 센다. 그때 🔥 키워드 전체를 읽어 지금 식으로 구한 값이 저장값과 다른 것만 그 키워드 행을 잠그고 고친다(키워드 행만 잠그므로 플레이어 → 키워드 잠금 순서와 부딪히지 않는다). 값이 바뀌면 (키워드 번호, 변화량)을 두 곳에 알린다. 접속 중인 보유자에게는 방 메모리 이벤트로 바로(9.2), 접속 중이 아닌 보유자에게는 메모리의 "변경됨" 집합으로. 외부 수집, 💤↔🔥 전환, 가중치·설정 변경 때도 같은 방식이다.
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
| 이동 명령 | 클릭 이동 초당 10회, 방향키는 바뀔 때마다(16ms에 한 번까지, 클라이언트 기준). 서버는 종류별 토큰 버킷으로 센다: 클릭 초당 12개(최대 24개까지 모임), 방향키 초당 60개(최대 60개), 그 밖의 종류 초당 10개. 고정 1초 창은 네트워크·프록시가 메시지를 몰아서 전달할 때 한도를 넘겨 그 1초 동안 모든 명령을 버리고, 화면만 움직였다가 되돌아오게 해서 쓰지 않는다. `ping`은 세지 않는다 |
| 이모트 | 게임 WebSocket 연결마다 토큰 버킷: 초당 0.5개(최대 3개 모임). 넘으면 조용히 버린다(4.26) |
| 채팅 | 1초에 1회(게임 WebSocket 연결마다 토큰 버킷, 넘으면 `chatError` `CHAT_TOO_FAST`) + IP 단위 |
| 정보 팝업 조회 | 계정당 초당 2회, 결과 5초 캐시 |
| 댓글 투표 | 사용자당 분당 20회, 같은 댓글 분당 5회(`vote:comment:{user}:{comment}`) |
| 색·칭호 변경 | 5초에 1회 |
| 꾸미기 프로필 저장·장착 | 5초에 1회 |
| 픽셀 그림 변경 | 모든 칸을 합쳐 `art_change_minutes`(기본 1분)에 1회(DB의 `image_changed_at`으로 확인, 재시작해도 유지, 409 `ART_TOO_SOON`). 이미지 업로드를 붙이면 1시간에 1회 |
| 외부 온디맨드 조회, 새 키워드 생성 | 사용자·IP 단위 분당 제한 |
| 트렌드 상자 열기 | 초당 2회 |
| 토큰 발급·갱신·게스트 시작(`/api/auth/*`) | IP 단위 분당 10회 |
| 자랑하기 카드 공유 주소 만들기 | 사용자당 분당 10회(429 `SHARE_TOO_FAST`). 값이 같은 카드는 같은 주소를 돌려주므로 행이 늘지 않는다 |

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
| `GET /api/me?country=KR\|GLOBAL` | 그 나라 플레이어(없으면 아직 닉네임 전)를 돌려주고 `users.country_id`(마지막으로 플레이한 지역)를 고친다. 모르는 나라·닫힌 나라는 400 |
| (공통) `Accept-Language: ko\|en` | 없으면 ko. 칭호·가이드·가이드 카테고리·공지·이동/테두리 효과 이름·잠금 사유는 기존 필드에 그 언어 값을 담아 보내고, 영어 값이 비어 있으면 한국어로 대체한다. 서버 오류 메시지는 한국어 그대로이며 화면이 오류 코드로 번역한다(8) |
| `PATCH /api/me/nickname` | `{ "nickname": "..." }`. 현재 나라 닉네임을 바꾼다(4.1). 규칙 위반 400, 지금과 같으면 400 `NICKNAME_SAME`, 변경 간격 전이면 409 `NICKNAME_TOO_SOON`. 응답은 플레이어 요약이고 `nicknameChangeableAt`(다음 변경 가능 시각, 없으면 바로 가능)을 담는다 |
| `POST /api/me/profile/copy` | `{ "profile_id": 123 }`. 내가 접속한 방 플레이어가 장착 중인 프로필만 복사할 수 있다. 바탕색·테두리 색·테두리 두께를 내 새 프로필(성장형)로 만들어 장착하고(픽셀 그림·업로드 이미지 제외), 레벨 조건에 맞지 않으면 거절한다 |
| `GET /api/emergency` | 지금 보여 줄 긴급 안내(로그인 없음, 4.29). `{notice: {id, message, messageEn, endsAt, remainingSeconds} \| null}`. `remainingSeconds`는 서버 시계 기준 남은 초라 클라이언트가 끝나는 때를 시계 차이 없이 정한다 |
| `GET /api/notices` | 공지 목록(고정 먼저, 최신순, 최대 50). `edited`(수정됨), `fresh`(7일 이내, NEW), `popup`(팝업 기간 안, 4.16). 로그인 없이 읽는다 |
| `GET /api/notices/popup`, `POST /api/notices/popup/seen` | 서버에 남기는 팝업 본 기록(`user_notices`). 아직 없음(지금은 `popup`과 브라우저 저장으로 판단, 4.16) |
| `GET /api/admin/guide/categories`, `POST /categories`, `PUT /categories/{id}`, `POST /categories/{id}/move`, `DELETE /categories/{id}`, `GET`·`POST /categories/{id}/articles`, `PUT`·`DELETE /articles/{id}`, `POST /articles/{id}/move` | 관리자 가이드 관리(13.4). 카테고리 `{ "code", "name", "nameEn", "displayType": "ARTICLE"\|"FAQ", "enabled" }`(code는 만들 때만, 400 `GUIDE_CODE`, 409 `GUIDE_CODE_EXISTS`), 글이 있는 카테고리 삭제 409 `GUIDE_CATEGORY_NOT_EMPTY`. 글 `{ "title", "body", "titleEn", "bodyEn" }`(영어 칸은 선택, 제목 1~100자, 본문 1~20,000자, 400 `GUIDE_TITLE`·`GUIDE_BODY`), 삭제는 소프트. 순서 `{ "direction": -1\|1 }`는 한 칸 옮기고 전체를 1부터 다시 매긴 목록을 돌려준다 |
| `GET`·`POST /api/admin/notices`, `PUT`·`DELETE /api/admin/notices/{id}` | 관리자 공지 목록(팝업 설정 포함)·등록·수정·삭제(소프트) `{ "title", "body", "titleEn", "bodyEn", "pinned", "popupEnabled", "popupDays" }`(영어 칸은 선택). 제목 400 `NOTICE_TITLE`, 본문 400 `NOTICE_BODY`, 팝업 기간 400 `NOTICE_POPUP_DAYS`, 없는 공지 404 `NOTICE_NOT_FOUND`(4.16) |
| `GET /api/me/score-history?range=24h\|7d\|30d\|all` | 내 점수 추이(현재 나라). 점 `{t, score, live}`, 기록이 없는 칸은 직전 값, 마지막 점은 지금 값 |
| `GET /api/room/players/{entity_no}` | 같은 방 다른 플레이어 정보(4.11): 닉네임, 관리자 여부, 활성 칭호, 레벨, 현재 유행도, 댓글 점수, 보관함 상위 3개(이름·상태). 계정당 초당 2회(429 `INFO_TOO_FAST`) |
| `GET /api/room/players/{entity_no}/score-history?range=7d` | 내가 접속한 방의 다른 플레이어(7일만). 그 방에 없는 번호면 404. 7일 외의 range는 400 |
| `GET /api/admin/users`·사용자 조치 API | 사용자 관리(13.10)는 선택 쿼리 `?country=KR\|GLOBAL`을 받는다(없으면 KR). 대시보드·통계·AI 설명도 같다 |
| `GET /api/me/admin` | 관리자인지만(`{ "admin": true }`). 관리자 화면과 게임의 [🛠 관리자] 버튼이 부른다 |
| `GET /api/admin/keywords?country=&state=&source=&q=&sort=&dir=&page=&size=` | 관리자 키워드 목록(13.6). state는 상태 이름, source는 소스 이름, sort는 trendValue·holderCount·totalPlusCount·searchCount·createdAt·activatedAt·externalUntil, 50개씩 |
| `GET /api/admin/keywords/lookup?country=&text=` | 키워드 한 개 조회(13.6). `{text, normalized, valid, reason, keyword}`: 규칙에 맞지 않으면 `valid=false`와 이유, 등록 전이면 `keyword=null`. 목록 행(`AdminKeywordRow`)은 `normalized`·`viewCount`를 포함 |
| `GET /api/admin/emergency`, `POST /api/admin/emergency`, `POST /api/admin/emergency/{id}/cancel` | 관리자 긴급 안내(13.14, 4.29). 목록은 최근 20개와 상태(ACTIVE/ENDED/CANCELED), 올리기는 `{message(≤200자), messageEn(선택), minutes(1~720)}`, 새로 올리면 켜져 있던 것은 취소되고 접속 중인 모두에게 WebSocket으로 알린다 |
| `GET /api/admin/integrations`, `PATCH /api/admin/integrations/{code}` | 관리자 연동 바로가기(13.15). 목록 `[{code, label, url}]`, 수정은 `{url}`(http(s)만, ≤500자) |
| `POST /api/admin/keywords` | `{ "text": "...", "country": "KR" }`. 🔥활성 키워드 추가(반영 소스 admin, 7일, 13.7) |
| `POST /api/admin/keywords/ban-text` | `{ "text": "...", "country": "KR" }`. 글자로 바로 금지(13.1): 없는 단어면 금지 상태로 새로 등록(소스 internal), 있으면 금지. 공개 페이지·사이트맵 캐시도 비운다(4.28) |
| `POST /api/admin/keywords/{id}/ban`, `/unban` | 금지·해제(13.1). 금지하면 트렌드 값 0, 보유자 점수에서 바로 빠짐 |
| `GET /api/admin/reports?status=&page=`, `PATCH /api/admin/reports/{id}` | 신고 목록(대상 원문 포함, 30건씩)과 처리 상태 변경 `{ "status": "RESOLVED", "sameTarget": true }`(13.5). 목록 한 줄에 `targetUserId`(신고된 사람), `contentDeleted`, `targetSanctions`(지금 걸린 제재) |
| `POST /api/admin/reports/{id}/act` | 신고에서 바로 조치(13.5) `{ "sanction": "CHAT" \| "COMMENT" \| "UPLOAD" \| "BAN" \| null, "days": 1, "reason": "", "deleteContent": true, "sameTarget": true }`. 제재를 걸고(사유를 비우면 신고 번호·사유로), 댓글이면 지우고, 그 신고(와 같은 대상의 접수 신고)를 조치함으로. 응답 `{ changed, report }`. 대상 사용자가 없으면 400 `REPORT_NO_USER`, 댓글이 아닌데 지우기면 400 `REPORT_DELETE_TYPE` |
| `GET /api/admin/settings`, `PATCH /api/admin/settings/{name}` | 설정 목록과 값 변경 `{ "value": "12" }`. 숫자·정수·허용 범위 검사, 저장하면 캐시를 비워 바로 반영(13.3) |
| `GET /api/keywords/{id}` | 키워드 상세 카드(4.12). 내 나라 키워드만, 금지 키워드는 404. `aiComment`(AI 한 줄 설명, 없으면 null)와 `aiCommentRequested`(내가 갱신 요청을 해 둔 상태), `viewCount`(공개 페이지·팝업 조회수, 저장 전 값과 이번 조회 포함, 4.28)를 함께 준다. 이 요청을 부르면(팝업 열기) 조회수에 센다(같은 방문자 하루 1번, 크롤러·User-Agent 없는 요청 제외) |
| `POST /api/keywords/{id}/ai-comment/request` | AI 설명 갱신 요청(4.12). 설명이 있는 키워드에만(409 `AI_COMMENT_NONE`), 같은 사람의 열린 요청은 하나(다시 눌러도 그대로), 1시간 10회(429 `AI_REQUEST_RATE`) |
| `GET /api/admin/ai-comments?filter=requests\|missing\|all&page=`, `POST /{keywordId}/regenerate`, `PUT /{keywordId}` `{ "comment": "…" }`, `POST /{keywordId}/dismiss` | 관리자 AI 설명(13.12). 갱신 요청 목록·설명 없는 키워드·설명 있는 키워드, AI 재요청(저장하고 열린 요청을 닫음. 키 없음 409 `AI_DISABLED`, 한도 429 `AI_RATE_LIMITED`, 실패 409 `AI_FAILED`, 모른다고 함 409 `AI_UNKNOWN`), 직접 수정(1~200자, 요청을 닫음), 요청 닫기 |
| `POST /api/keywords/{id}/comments` | `{ "body": "..." }`. 댓글 쓰기(1~200자, 최소 글자 수 제한 없음, 링크·전화번호 불가). 작성자 댓글 점수 +1 |
| `PATCH /api/comments/{id}`, `DELETE /api/comments/{id}` | 내 댓글 고치기(작성 후 5분 안, 지나면 409 `COMMENT_EDIT_EXPIRED`), 지우기(소프트 삭제, 댓글 점수 −1) |
| `POST /api/reports` | `{ "target": "KEYWORD" \| "COMMENT" \| "CHAT" \| "PLAYER", "targetId": 1, "reason": "..." }`(PLAYER는 같은 방의 세션 엔티티 번호). 신고(13.5). 내 나라 대상만, 내 글은 400 `REPORT_OWN`, 같은 대상 두 번은 409 `REPORT_DUPLICATE` |
| `GET /api/rankings?country=` | 리더보드: 그 나라 방에 접속 중인 사람의 현재 유행도 TOP 10(4.14) |
| `POST /api/auth/withdraw` | `{ "phrase": "탈퇴 합니다." }`. 탈퇴(4.1): 문구가 다르면 400 `WITHDRAW_PHRASE`, 게스트면 400 `WITHDRAW_GUEST`. 갱신 토큰 폐기, 쿠키 삭제(204) |
| `GET /api/levels/badges` | 레벨 표시 옆 성장 아이콘의 바탕(레벨별 색·진하기·무지개 여부, `levels`에서 읽음, 4.9) |
| `GET /api/me/titles`, `PUT /api/me/title` | 칭호함(전체 칭호, 보유·획득일·진행도·활성)과 활성 칭호 바꾸기 `{ "titleId": 3 }`(null이면 해제). 안 가진 칭호는 400 `TITLE_NOT_OWNED`, 5초 1번 429 `TITLE_TOO_FAST`(4.10) |
| `GET /api/me/profiles` | 내 꾸미기 프로필(4.9): 1~3번 칸(`saved`·`equipped`·`inner`(GROWTH/PIXEL)·모습(효과 코드 포함)·그림·효과 번호·저장 시각), `equippedSlot`, `unlocked`·`unlockLevel`, 기본 모습, 프리셋 24색, 이름표 모양, `canDraw`·`drawLockReason`·`artChangeableAt`, 고를 수 있는 이동 효과 `trails`·테두리 효과 `borders`(번호·코드·이름·`unlocked`·`lockReason`) |
| `PUT /api/me/profiles/{slot}` | 한 칸 저장 `{ "background", "borderColor", "borderWidth", "nameColor", "nameStyle", "inner": "GROWTH" \| "PIXEL", "art": { "palette": [...], "pixels": "1024글자" } \| null(그대로), "trailEffectId", "borderEffectId", "equip": false }`. equip이면 저장 후 장착. 칸 번호 400 `PROFILE_SLOT`, 11레벨 전 403 `CUSTOMIZE_LOCKED`, 색 400 `COLOR_FORMAT`·`COLOR_CONTRAST`·`NAME_CONTRAST`, 두께 400 `BORDER_WIDTH`, 이름표 모양 400 `NAME_STYLE`, 그림 400 `ART_FORMAT`·`ART_COLORS`·`ART_EMPTY`·`ART_NONE`, 게스트 그림 403 `ART_LOGIN`, 그림 저장 정지 403 `ART_BANNED`, 그림 간격 409 `ART_TOO_SOON`, 효과 404 `EFFECT_NOT_FOUND`·403 `EFFECT_LOCKED`, 5초 1번 429 `LOOK_TOO_FAST`. 장착한 칸이면 방에 바로 반영 |
| `PUT /api/me/profiles/equipped` | 장착할 칸 `{ "slot": 2 }`. 빈 칸은 기본 모습으로 만들어 장착. 11레벨 전 403, 5초 1번 429 |
| `GET /api/art/{profileId}.png?v=` | 픽셀 그림 PNG(32×32, 로그인 없음). 서버가 저장한 값으로 그려 메모리에 캐시하고 `Cache-Control: public, max-age=31536000, immutable`. 그림 프로필이 아니면 404 |
| `GET /api/admin/stats/summary`, `/hourly?hours=`, `/daily?days=` | 관리자 통계(13.9). summary: 지금 접속, 오늘·어제 값, 사용자 구성(전체·게스트·Google·관리자·탈퇴·금지·플레이어·7일·30일 활동), 키워드 상태별 수, 처리 대기 신고, 레벨 분포. hourly(최대 744시간)·daily(최대 366일): 오래된 순, 기록 없는 칸은 0, 마지막 칸은 집계 중(`live`) |
| `GET /api/admin/dashboard?country=` | 관리자 대시보드(13.11): 지금 접속, 오늘·어제 값, 사용자 구성, 최근 24시간 시간별 값, 처리할 일 목록(`key`, `label`, `count`, `level` danger·warn·ok, `detail`, 누르면 열 `tab`·`filter`), 최근 7일 신고 많이 받은 5명, 키워드 상태별 수·오늘 새 키워드·가진 사람 많은 🔥 5개, 구글 실시간 인기 마지막 수집 시각, 리더보드 5명, 새 플레이어 5명, 최근 공지 5개, 자랑하기 카드 수 |
| `GET /api/admin/users?q=&filter=&sort=&dir=&page=` | 관리자 사용자 목록(13.10, 30명씩). q는 닉네임 일부 또는 사용자·플레이어 번호, filter는 all·online·google·guest·admin·banned·withdrawn, dir은 desc(기본)·asc, sort는 seen·created·score·level·plus |
| `GET /api/admin/users/{userId}` | 사용자 상세(13.10): 계정·플레이어 값, 누적 수치, 꾸미기, 보관함, 최근 댓글·채팅 10개, 받은 신고 20개, 제재 이력 |
| `POST /api/admin/users/{userId}/sanctions`, `POST /api/admin/users/sanctions/{id}/release` | 제재 걸기 `{ "type": "BAN" \| "UPLOAD" \| "COMMENT" \| "CHAT", "days": 7, "reason": "…" }`(days가 null이면 영구, 1~3650일, 사유 1~500자, 관리자 계정 금지는 400 `SANCTION_ADMIN`)와 풀기(이미 끝났으면 409 `SANCTION_NOT_ACTIVE`) |
| `PATCH /api/admin/users/{userId}/nickname`, `POST /{userId}/look/reset`, `PATCH /{userId}/role`, `POST /{userId}/kick` | 닉네임 바꾸기(닉네임 규칙 그대로, 변경 간격은 세지 않음), 꾸미기 초기화, 관리자 권한 `{ "admin": true }`(Google 계정만, 내 권한 빼기 400 `ROLE_SELF`), 강제 퇴장(접속 중이 아니면 409 `NOT_ONLINE`) |
| `POST /api/share-cards` | 자랑하기 카드 공유 주소 만들기(4.24). 본문 없음, 게스트 가능. 서버가 지금 값(닉네임, 칭호, 레벨, 유행도, 순위, 7일 점수, 🔥 키워드 3개, 누적 기록, 꾸미기, 이동 효과)을 모아 저장하고 `{ "code", "url", "imageUrl", "totalViews", "totalPlays" }`을 돌려준다(`total*`는 내가 만든 모든 카드의 조회·클릭 합계). 같은 플레이어의 값이 모두 같은 카드가 있으면 그 주소를 그대로 준다. 닉네임이 없으면 404 `NO_PLAYER`, 분당 10번 429 `SHARE_TOO_FAST` |
| `GET /share/{code}` | 공유 페이지 HTML(로그인 없음). OG·트위터 카드 태그와 카드 이미지, [플레이 하러 가기]. 없는 코드는 404 페이지. 조회 수를 센다(같은 방문자 카드마다 하루 1번, 크롤러 제외)라서 `Cache-Control: no-cache` |
| `GET /share/{code}/play` | [플레이 하러 가기]. 클릭 수를 세고(조회와 같은 규칙) `/?ref=share`로 302. 없는 코드도 메인으로 보낸다 |
| `GET /api/admin/share-cards?sort=views\|plays\|createdAt&page=` | 관리자 공유 통계(13.8): 만든 카드 수, 조회·클릭 합계, 오늘 만든 카드 수, 카드 목록 30개씩(코드, 닉네임, 조회, 클릭, 만든 때, 주소) |
| `GET /{kr\|global}/keywords/{slug}` | 공개 키워드 페이지 HTML(로그인 없음, 4.28). 금지·없는 키워드는 404. 열 때마다 조회수를 세므로 `no-cache` |
| `GET /sitemap.xml` | 사이트맵(로그인 없음, 5분 캐시). 메인 주소, 금지가 아닌 모든 키워드 페이지, 공유 페이지(`/share/{코드}`)를 한 파일로, `lastmod` 포함. 프론트 정적 파일이 아니라 백엔드가 만든다(4.28) |
| `GET /share/{code}/card.png` | 카드 이미지(로그인 없음). 저장한 값으로 서버가 그린다(Java2D, 글꼴은 jar에 넣은 나눔스퀘어 ac). 그린 결과는 메모리에 최대 64MB 캐시하고, `Cache-Control: public, max-age=31536000, immutable` |
| `GET /api/guide/categories`, `/categories/{code}/articles`, `/articles/{id}`, `/search?q=` | 가이드(5분 캐시, 수정 시 즉시 삭제). 로그인 없이 읽는다. 지금은 앞의 두 개만 있고 글 목록에 본문을 같이 준다 |

- 투표 토글: 없음 → INSERT, 같은 값 → DELETE, 반대 값 → UPDATE. 조건부 쿼리의 영향 행 수가 1일 때만 `comments.like_count`·`dislike_count`를 바꾸고, 같은 때 작성자의 `comment_score`도 같이 바꾼다(추천 +1, 비추천 −1, 취소·변경은 반대로, `GREATEST(값, 0)`으로 0 아래 방지). 첫 투표 경합은 UNIQUE 오류 후 다시 읽어 처리한다.

### 9.7 배치 잡

| 주기 | 잡 |
|---|---|
| 5초 | 접속 중인 플레이어의 바뀐 점수를 `players.current_score`에 모아서 저장(9.2) |
| 10초 | 관리자 통계(13.9): 나라별 방 접속 인원 표본(이번 시간·오늘의 최고·평균·활동 인원을 메모리에). 1분마다 접속 중인 플레이어의 `players.last_seen_at`을 고친다 |
| 1분 | 자랑하기 카드 조회·클릭 수 저장(메모리에 모은 증가분을 `share_cards.view_count`·`play_count`에 더함, 종료 때도 저장, 4.24). 만료된 `+` 삭제와 `holder_count`·`hold_count` 정리, 나라별 활동 인원을 다시 세고 지금 식과 달라진 🔥 키워드의 트렌드 값 갱신(4.8), 트렌드 값이 바뀐 키워드의 접속 중이 아닌 보유자 점수 재합산(9.4) |
| 매시 정각 | 플레이어 점수 기록(`player_score_24h`의 그 시간 칸과 `player_score_dailies`의 그날 행을 현재 점수로 덮어쓰기), 키워드 카운터 저장(`keywords.search_count` 갱신 후 `keyword_log_24h`의 그 시간 칸 덮어쓰기, 그날의 `keyword_log_dailies` 다시 만들기), 180일이 지난 `keyword_log_dailies` 행 삭제(9.9), 전체 점수 재합산 점검(9.4), 사이트 통계(13.9: 지난 한 시간의 `site_hourly_stats` 행을 쓰고 그날의 `site_daily_stats`를 시간별 행에서 다시 만들기. 서버를 끌 때도 이번 시간 값을 저장) |
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
| google | 구글 트렌드 실시간 인기 RSS(`https://trends.google.com/trending/rss?geo=KR`, GLOBAL은 `geo=US`) | KR, GLOBAL | 지금 급상승 중인 검색어 상위 10개의 제목(`title`). 대략 검색량·관련 뉴스도 오지만 쓰지 않는다 | 약 10분(5분마다 조회) |
| admin | 관리자가 직접 넣은 키워드(13.7) | 전체 | 관리자 화면에서 넣은 🔥활성 키워드. 넣은 때부터 7일 유지 | - |
| internal | 서비스 내부 | 전체 | 외부에서 확인되지 않은 키워드의 반영 소스. 점수는 가진 사람 수만으로 정함(4.8) | - |

- 구글 실시간 인기는 공식 API가 아니라 구독용으로 공개된 RSS다. 인증·한도가 없고 `robots.txt`가 막지 않는 경로지만, 형식이 바뀌거나 막힐 수 있다. 그래서 읽지 못해도 게임은 그대로 돈다(10.2). 정식 출시 전에 상업적 이용 가능 여부를 확인한다(16.2). 구글 도움말은 출처 표기("Data source: Google Trends")를 요구한다.
- 쓰지 않는 것: Google Ads 키워드 플래너, Google Trends API(알파), 구글 트렌드 웹 화면 스크래핑(내부 API `batchexecute` 포함), Custom Search JSON API, Search Console API, 네이버 데이터랩·검색 API, BigQuery Google Trends 공개 데이터셋, 위키백과, YouTube, 바이두, 웨이보, Yahoo! JAPAN.
- 개발 프로필의 외부 소스 흉내(`dev-trends`)는 KR 키워드 외에 GLOBAL용 영어 키워드 5개(`trendist.dev-trends.global-keywords`)도 넣는다.
- 나라별로 쓰는 소스는 위 표의 '지역' 열을 기준으로 코드(설정)에 둔다. `data_sources`에는 나라 열이 없고 소스마다 가중치 하나만 있어서, 같은 소스를 여러 나라가 쓰면 가중치를 공유한다.
- GLOBAL은 미국(`geo=US`) 구글 트렌드로 정했다(소스 이름 `google`은 KR과 같고, 가중치도 공유한다). JP·IN은 외부 소스를 정한 뒤 연다(16.1). 나라별로 독립 수집해서 한 나라나 소스가 막혀도 나머지는 계속된다.

### 10.2 수집 방식
- 구글 실시간 인기(google, KR·GLOBAL): 열린 나라마다 수집 루프를 따로 돌린다(나라별 `geo`로 RSS를 읽고, 키워드·유지 기간 `external_until`·만료 처리도 그 나라 안에서만 한다). 아래 설명은 한 나라 기준이다.
  - 5분마다 RSS를 읽는다. 각 제목(`title`)에서 키워드에 쓸 수 없는 문자(`:` 같은 문장 부호)는 공백으로 바꾸고 4.6 등록 조건 검사를 거친다. 통과하지 못한 제목은 버린다.
  - 처음 올라온 제목은 키워드를 새로 만들어 🔥활성, 반영 소스 `google`로 시작한다. 이미 있는 키워드(금지 제외)는 반영 소스를 `google`로 바꾸고 🔥로 만든다. 이때부터 7일 동안 외부 데이터에 의한 활성으로 유지한다(`keywords.external_until` = 지금 + 7일, 설정값 `external_hold_hours` 기본 168). 목록에 다시 보일 때마다(5분마다) 지금 시각부터 7일로 다시 센다.
  - 유지 기간이 끝나면(5분마다 검사, 마지막으로 읽은 목록 기준) 지금 목록에 아직 있으면 지금부터 다시 7일 유지한다. 목록에 없으면 반영 소스를 `internal`(내부 데이터)로 바꾸고, 가진 사람이 활성 조건(3명) 이상이면 🔥를 유지, 미만이면 💤비활성으로 바꾼다. 트렌드 값과 보유자 점수도 다시 계산한다.
  - 사용자가 검색한 단어가 지금 목록에 있으면 외부 확인으로 보고 같은 방식(반영 소스 `google`, 7일 유지)으로 등록한다.
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
| GLOBAL | google / admin / internal | 1 / 1 / 1 |
| JP·IN | internal(외부 소스 미정, 16.1) | 1 |

- 나라마다 외부 소스는 하나이고, 한 키워드는 한 번에 반영 소스를 하나만 가진다(`internal` 아니면 그 외부 소스). 외부 소스를 더 붙이면 외부 확인된 소스 중 가중치가 큰 쪽을 쓴다. 트렌드 값의 식은 4.8에 있다.
- 가중치는 소스마다 하나이고 0~2다(식의 최대 기준값 50 × 최대 가중치 2 = 100 상한 때문). 0이면 그 소스를 반영 소스로 쓰지 않는다(외부 확인에서도 제외). 지금은 모든 소스가 1이다.
- 위 표는 `data_sources`의 초기값이다. `internal`은 모든 나라가 같은 행을 쓴다. 기본 비율(3), 최대 기준값(50), 최고점 비율(0.5), 최소 인원(20), 로그 완만도(0.3), 외부 확인 관심도(10)는 `settings`에 둔다(13.3).

### 10.4 AI 한 줄 설명(제미나이)
- 연동: 구글 Gemini API의 REST `generateContent`. 모델은 `gemini-3.5-flash-lite`가 기본이고 환경 변수 `GEMINI_MODEL`로 바꾼다(2026-11 확인: `gemini-2.5-flash-lite`는 새 사용자에게 더는 열려 있지 않아 404가 난다). 키는 서버 환경 변수(로컬 `DEV_GEMINI_API_KEY`, 운영 `PROD_GEMINI_API_KEY`)에만 두고 브라우저로 보내지 않는다. 키가 없으면 기능이 꺼지고(카드에 설명 칸이 안 보임) 직접 수정은 된다. 구현은 `GeminiClient`(인터페이스, 시험에서는 가짜로 교체)·`GeminiClientImpl`.
- 언어: 키워드를 그 나라의 기본 언어별로 묶어 요청한다. KR은 한국어 요청문과 한국어 뉴스 RSS, GLOBAL은 영어 요청문과 미국 영어 뉴스 RSS(`hl=en-US`)를 쓰고, 저장되는 설명도 영어다. 아래 요청문 설명은 한국어 기준이다.
- 요청문: 사전식 뜻풀이가 아니라 "지금 왜 화제인지"(사건·발표·방송·경기·출시·논란·밈)를 한국어 한 문장(90자 이내, 존댓말)으로 쓰게 한다. 키워드마다 RSS가 준 관련 뉴스 제목(`TrendNewsContext`, 최대 5개)을 붙여 보내고 그 제목만 근거로 쓰게 하며(제목을 베끼지 않고 풀어서), 제목이 없으면 확실히 알 때만 쓰고 아니면 `UNKNOWN`이다. 추측·루머·비방·사생활·선정적 표현·링크·이모지·마크다운은 금지한다. 뉴스 제목은 `GoogleTrendingSource`가 5분마다 RSS를 읽을 때 `ht:news_item_title`에서 꺼내 서버 메모리에 둔다(재시작하면 다음 읽기에 다시 채워지고, 최대 1000개).
- **뉴스 제목 기억의 키**: `TrendNewsContext`는 (설명 언어 ko·en, 정규화한 키워드)로 기억해, 철자가 같은 키워드가 KR과 GLOBAL에 모두 있어도 한국어·영어 뉴스 제목이 서로 덮어쓰지 않는다.
**RSS 밖 키워드의 뉴스 찾기**: 사용자가 직접 등록해 🔥가 된 키워드에는 RSS의 뉴스 제목이 없다. 이때는 `GoogleNewsSearchClient`가 구글 뉴스 검색 RSS(`news.google.com/rss/search?q=키워드 when:7d`, 키 없이 무료)에서 제목을 찾아 같은 방식으로 제미나이에 보낸다. 제목에 키워드(공백·대소문자 무시)가 든 것만 최대 5개 쓰고 " - 매체" 꼬리는 뗀다. 결과가 없거나 실패하면 빈 목록이라 AI는 "모름"으로 답하고 관리자가 직접 쓴다. 키워드당 시도는 최대 3번이라 검색도 그만큼만 나가고, 찾은 제목은 `TrendNewsContext`에 기억한다. 테스트·로컬에서 끄려면 `trendist.news-search.enabled=false`.
- **묶음 요청**: 키워드를 하나씩 보내지 않고 한 요청에 `ai_comment_batch_size`(기본 10)개를 번호를 붙여 보내고, 응답도 `[{no, comment}]` 배열로 받아 번호로 맞춘다. 실측(2026-11-03, 실제 구글 트렌드 제목 10개): 하나씩 보내면 요청 10번·9.4초·약 2,300토큰, 묶으면 요청 1번·2.1초·약 650토큰이었다(요청 수 1/10, 시간 약 1/4.5, 토큰 약 1/3.5). 무료 한도는 요청 수(분당·하루)가 기준이라 요청 수가 줄어드는 효과가 가장 크다. 시스템 요청문(약 200토큰)을 매번 되풀이하지 않는 것이 토큰 절감의 대부분이다. 대신 한 요청이 통째로 실패하면 묶인 키워드가 같이 실패(→ 다음 분에 다시)하고, 한 키워드의 답이 빠지면 그것만 다시 시도한다. 묶음 답에서 개별 요청에서는 모른다던 키워드를 그럴듯하게 지어낼 가능성이 있어(실측에서 1건) 묶음 크기를 10으로 두고 UNKNOWN 지시를 강하게 했다.
- 구글 검색 확인(그라운딩): 최신 이슈를 맞게 설명하려는 기능이지만 **무료 등급에서는 쓸 수 없다**(실측: 3.5 Flash-Lite·3.5 Flash 모두 429 "quota exceeded"). 그래서 기본은 꺼짐(`ai_comment_grounding` 0)이고, 결제를 연결한 뒤에 켠다. 꺼 두면 모델이 아는 범위에서만 설명해서 아주 최근 사건·신조어는 `UNKNOWN`이 늘 수 있다(실측 10개 중 2~3개). 그런 키워드는 "설명 없음"에 쌓이고 관리자가 직접 쓰거나(13.12) 나중에 재요청한다.
- 자동 생성: `AiCommentServiceImpl.generatePending()`이 1분마다 돌아, 한 번이라도 🔥가 됐고(`activated_at`) 설명이 없고 금지가 아니고 시도 횟수가 3 미만인 키워드를 최근 🔥가 된 것부터 한 요청에 묶어 보낸다. 요청은 1분에 한 번, 하루 최대 `ai_comment_daily_max`(기본 400)번이다. 요청이 실패하면 묶인 키워드의 시도 횟수를 올리고 다음 분에 다시, 모른다고 하면(`UNKNOWN`) 그 키워드는 바로 포기(시도 횟수 3)해 같은 질문을 되풀이하지 않는다. 제미나이가 한도(429)를 알려 오면 10분 동안 자동 요청을 쉰다. 설정 `ai_comment_enabled`를 0으로 하면 멈춘다.
- 사용자가 카드를 열 때는 DB 값만 읽는다. 그래서 접속자가 늘어도 AI 요청 수는 늘지 않는다. AI 요청은 (1) 새로 🔥가 되는 키워드 수(묶음 크기로 나눈 만큼), (2) 관리자의 [AI 재요청] 횟수에만 비례한다.
- 한도 검토(2026-11 기준, 공식 문서·커뮤니티 자료이며 바뀔 수 있다. 배포 전 AI Studio에서 프로젝트의 실제 한도를 확인한다):
  - 무료 등급 한도는 모델마다 다르고 일일 요청 수(RPD)·분당 요청 수(RPM)·분당 토큰 수가 있다. 한도는 프로젝트 단위, 일일 한도는 태평양 시간 자정에 초기화된다. 이전 세대 Flash-Lite는 하루 1,000·분당 15 정도였으며, 3.5 계열의 정확한 수치는 AI Studio에서 확인한다.
  - 예상 사용량: 구글 목록에서 새로 오르는 키워드와 사용자·관리자가 만든 활성 키워드를 합쳐 하루 수십~200개 안팎이다(사용자 1,000명이어도 같은 키워드를 공유하므로 크게 늘지 않는다). 묶음 10개면 하루 요청은 많아야 수십 번이고, 자동 요청은 분당 1번·하루 400번이 상한이라 무료 한도 안이다.
  - 무료 등급은 입력이 서비스 개선에 쓰일 수 있다는 약관이 있다. 보내는 것은 공개된 키워드 글자뿐이고 사용자 정보는 보내지 않는다.
  - 한도는 보장되지 않는다(모델 변경·종료 — 실제로 2.5 Flash-Lite가 종료됐다). 그래서 모델 이름을 환경 변수로 빼 두었고, 실패해도 게임은 그대로 돌며(설명 칸만 안 보임) 관리자가 직접 쓸 수 있다.

## 11. 데이터 모델

### 11.1 규칙
- 테이블 이름은 소문자 스네이크 케이스 복수형(마지막 단어만, 기간 접미사 `24h`는 복수형을 쓰지 않는다). 다대다 매핑은 `앞 단수_뒤 복수`(`player_keywords`). 외래 키 컬럼은 `참조 단수_id`이고(복수형의 s를 뗀다) 기본 키 바로 다음에 순서대로 둔다(ERD Cloud에 보이는 순서). 한 테이블에서 같은 테이블을 두 번 참조하는 컬럼은 두지 않는다(그래서 공지·가이드에는 마지막 수정 관리자 컬럼이 없다).
- 모든 테이블의 기본 키는 `id BIGINT AUTO_INCREMENT` 하나다. 관계는 모두 비식별, 복합 키 없음. "한 번만" 규칙은 `UNIQUE`로 지킨다.
- 시각 컬럼: 기본은 `created_at`·`updated_at`(`DATETIME(6)`, UTC, DB 기본값 + JPA Auditing). 고치지 않는 기록 테이블(`*_histories`, `user_notices`, `blocked_terms`, `chat_messages`)은 `created_at`만이다. 칸을 덮어쓰거나 매시간 다시 쓰는 `player_score_24h`, `player_score_dailies`, `keyword_log_24h`, `keyword_log_dailies`는 `updated_at`도 둔다. 업무 시각(`expires_at`, `banned_at` 등)은 따로 둔다.
- 정수 컬럼은 확장에 대비해 모두 `BIGINT`로 둔다(점수, 인원, 횟수, 순서, 레벨 등). 참/거짓은 `TINYINT(1)`, 0~23 시간 칸 같은 작은 번호는 `TINYINT`로 둔다. JPA 엔티티 필드도 `long`이다.
- 코드값은 `ENUM` 대신 `VARCHAR` 대문자 코드(`countries`, `users.role` 등) 또는 이름만 있는 코드 테이블(`user_states`, `keyword_states`, `report_types`, `sanction_types`)로 둔다. 허용 값 검증은 `CHECK` 없이 애플리케이션에서 한다.
- 같은 단어 판정 컬럼(`keywords.normalized`, 차단어)은 `utf8mb4_bin`. 정규화는 애플리케이션에서 한다.
- JPA 엔티티는 단수형 클래스 + `@Table(name = "users")`. 스키마는 Flyway 마이그레이션(`src/main/resources/db/migration`)으로 바꾸고, 시더가 초기 데이터를 넣는다(11.3). 자동 테스트만 시작할 때 JPA가 H2에 스키마를 만든다.

### 11.2 테이블 (39개, 컬럼은 `4-schema.sql`)

| 분류 | 테이블 | 역할 |
|---|---|---|
| 사용자 | `user_states` | 사용자 상태(정상 / 탈퇴) |
| | `users` | 계정(게스트 포함). 방문자 ID, OAuth 종류·ID, 상태, 권한, UI 언어, 계정 금지·업로드 정지 만료 시각, 탈퇴 시각 |
| | `players` | 플레이어 = 사용자 × 나라. 닉네임, 누적 검색 횟수, EXP·레벨, 활성 칭호, 현재 유행도(실시간 점수), 보유 키워드 수, 마지막 접속 시각, 누적 `+` 횟수, 댓글 점수 |
| | `profiles` | 꾸미기 프로필(4.9): 플레이어 × 1~3번 칸. 바탕·테두리 색과 두께, 이름표 글자색·모양, 원 안에 그릴 것(성장 캐릭터 / 픽셀 그림)과 그 칸의 그림 값, 이동 효과·테두리 효과 번호. 한 칸만 장착 |
| | `titles`, `player_titles` | 칭호 목록(영어 이름·설명 `name_en`·`description_en`, 비어 있을 수 있음)과 플레이어별 보유 칭호 |
| | `effect_types`, `effects` | 효과 종류(TRAIL 이동 효과 4.23 / BORDER 테두리 효과 4.9, 영어 이름 `name_en`)와 원을 꾸미는 효과 목록·얻는 조건. 쓰는 효과는 꾸미기 프로필의 `trail_effect_id`·`border_effect_id` |
| | `levels` | 레벨별 필요 누적 EXP(1~100레벨, 관리자가 수정) |
| | `refresh_tokens` | 계정 로그인 세션(갱신 토큰 해시, 만료·폐기) |
| 키워드 | `countries` | 나라 설정(시간대, 오픈 여부, 방 상한) |
| | `keyword_states` | 키워드 상태(활성 / 비활성 / 금지) |
| | `keywords` | 나라별 키워드. 표시용·정규화 단어, 상태, 반영 소스, 트렌드 값, 누적 검색 수·`+` 수, 가진 사람 수, 공개 페이지 조회수(`view_count`) |
| | `player_keywords` | 보관함. `+` 한 번 = 한 행, 24시간 뒤 삭제 |
| | `player_positions`, `room_states` | 방 메모리 상태 저장(9.2): 플레이어의 마지막 위치와 저장 시각(플레이어당 한 행), 나라의 활동 영역과 저장 시각(나라당 한 행). 서버를 다시 켜도 5분 안이면 이어 간다 |
| | `keyword_ai_requests` | AI 설명 갱신 요청(4.12): 키워드 × 요청한 플레이어, 처리 시각(`handled_at`이 NULL이면 처리 전). |
| | `keyword_ai_comments` | 키워드 AI 한 줄 설명(4.12): 설명, 쓴 쪽(AI/ADMIN), 쓴 시각, 자동 시도 횟수. `keywords.ai_comment_id`가 가리키고(키워드 하나에 행 하나, 처음 만들기를 시도할 때 만듦) 키워드 행은 가볍게 둔다 |
| | `keyword_log_24h`, `keyword_log_dailies` | 키워드 활동 기록. 시간별(키워드당 24칸 덮어쓰기)과 현지 하루별(180일), 누적 스냅샷과 증분 |
| | `data_sources` | 데이터 소스별 반영값(가중치) |
| 소셜 | `comments`, `comment_votes` | 키워드 댓글과 투표 |
| | `chat_messages` | 채팅(30일 보관) |
| | `share_cards` | 자랑하기 카드 공유 주소(4.24). 공개 코드와 공유한 순간의 카드 값(JSON, 고치지 않음), 누적 조회·클릭 수, 영구 보관 |
| | `report_types`, `reports` | 신고 대상 유형과 신고(대상 유형 + ID) |
| 운영 | `notices`, `user_notices` | 공지(영어 `title_en`·`body_en`)와 팝업 열람 기록 |
| 운영 | `emergency_notices` | 긴급 안내(4.29): 만든 사람, 내용·영어, 끝 시각, 취소 시각. 마이그레이션 V3 |
| 운영 | `integration_links` | 관리자 연동 바로가기(13.15): code·이름·주소·순서. 기본 3개는 서버가 채움. 마이그레이션 V4 |
| | `guide_categories`, `guides` | 게임 가이드(영어 `name_en` / `title_en`·`body_en`) |
| | `player_score_24h`, `player_score_dailies` | 점수 추이 로그(플레이어 1 : 기록 N). 시간별은 플레이어당 24칸 덮어쓰기, 하루별은 영구 |
| | `settings`, `setting_histories` | 밸런스 설정과 변경 이력 |
| | `blocked_terms`, `blocked_nicknames` | 차단어, 금지 닉네임 |
| | `sanction_types`, `sanctions` | 제재 타입과 사용자 제재 기록(사유·기간·해제) |
| | `site_hourly_stats`, `site_daily_stats` | 관리자 통계(13.9). 나라 × 한 시간 / 나라 × 현지 날짜: 최고·평균 동시 접속, 활동 인원, 새 플레이어, 추가·검색·구슬·충돌로 잃기·채팅·댓글·자랑하기 카드 수 |

- `player_keywords.player_id`의 나라와 키워드의 나라가 같은지, `chat_messages.country_id`와 `player_id`의 나라가 같은지, 댓글·투표의 `player_id`가 그 댓글 키워드의 나라인지는 애플리케이션에서 검사한다. `keywords.data_source_id`(반영 소스)는 항상 값이 있고, 외부에서 확인되지 않은 키워드(사용자가 만든 것, 관리자가 넣은 시드 포함)는 `internal`을 가리킨다. `reports.target_id`는 대상이 여러 테이블이라 외래 키가 없다.
- 계속 쌓이는 테이블은 `keyword_log_dailies`(180일), `player_score_dailies`(영구), `share_cards`(영구, 행당 1KB 안팎), `site_hourly_stats`(나라당 하루 24행, 영구), `site_daily_stats`(나라당 하루 1행, 영구)다. 커지면 월 파티션으로 나누고 기본 키를 각각 `(id, record_date)`로 바꾼다. `player_score_24h`는 플레이어당, `keyword_log_24h`는 키워드당 최대 24칸을 덮어써서 항상 24시간치만 남는다. `player_keywords`는 하루치만 남는다.

### 11.3 스키마 마이그레이션(Flyway)과 초기 데이터
스키마 (Flyway)
- 스키마는 Flyway가 바꾼다. 마이그레이션 파일은 `src/main/resources/db/migration/V{번호}__{설명}.sql`이고, 애플리케이션이 시작할 때 아직 안 한 것만 번호 순서로 실행한다(이력은 DB의 `flyway_schema_history`). 로컬 개발(dev)과 운영(prod)이 켜고, 자동 테스트는 끈다(H2에 JPA가 스키마를 만든다).
- **DB를 바꾸는 방법**: 엔티티에 컬럼·테이블·인덱스를 추가하거나 바꾸면 같은 작업에서 새 파일 `V{다음 번호}__설명.sql`을 만든다(예: `V2__keyword_view_count.sql`). 이미 적용된 파일(V1, V2 …)은 고치지 않는다. 컬럼 이름·타입 변경, 삭제, 데이터 옮기기도 새 파일에 SQL로 쓴다. 파일 내용을 바꾸면 체크섬 불일치로 시작이 거부된다.
- `V1__baseline.sql`은 Flyway 도입 시점(2026-11-05)의 엔티티가 만드는 스키마(37개 테이블)다. **Flyway 도입 전부터 있던 DB(운영·로컬)는 V1을 실행하지 않는다.** 스키마는 있는데 이력 테이블이 없으면 V1로 기준선만 찍고(`baseline-on-migrate=true`, `baseline-version=1`) V2부터 적용한다. 빈 DB는 V1부터 모두 실행해 처음부터 만든다.
- 로컬 개발은 `ddl-auto=validate`다. 엔티티와 DB가 다르면(마이그레이션을 빠뜨리면) 시작할 때 오류로 알려 준다. 운영은 `ddl-auto=none`이다(운영 DB가 엔티티와 같다고 확인되면 `validate`로 올린다, 16.1).
- 엔티티가 만드는 스키마와 마이그레이션 결과가 같은지는 빈 DB로 확인한다: 새 스키마에서 시작해 V1~V최신이 실행되고 `validate`를 통과해야 한다(2026-11-05 로컬 MariaDB 10.11로 확인, MySQL 8은 미확인, 16.2). `4-schema.sql`(17절)은 사람이 읽는 참고용 전체 스키마이므로 마이그레이션을 추가하면 같이 고친다.
- 자동 테스트는 H2 메모리 DB에 JPA(`ddl-auto`)로 스키마를 만들어서 마이그레이션 SQL은 테스트하지 않는다. H2에서 실행되지 않는 SQL이 들어가도 테스트로는 잡히지 않는다.

빠른 초기화
- `reset` 프로필(또는 환경 변수 `TRENDIST_DB_RESET=true`)로 실행하면 스키마의 모든 테이블(`flyway_schema_history` 포함)을 지우고 Flyway가 V1부터 다시 만든 뒤 초기 데이터를 넣는다. 엔티티에 없는 옛 테이블까지 지우려고 `information_schema`의 테이블 목록을 읽어 외래 키 검사를 끄고 지운다. Flyway·Hibernate가 스키마를 다루기 전에 실행되는 초기화 빈이 맡는다.
- 안전장치: DB 주소가 로컬(localhost 등)이고 `reset` 프로필일 때만 동작한다. 운영 프로필과 같이 켜져 있으면 시작을 거부한다. 지우기 전에 대상 스키마 이름을 로그에 남긴다.

초기 데이터
- 시작할 때마다 시더(`ApplicationRunner`)가 스키마 반영이 끝난 뒤 필요한 테이블을 검사해서, 비어 있으면 초기값을 자동으로 넣는다. 그래서 `reset` 직후나 새 DB에서도 바로 테스트할 수 있다.
- 코드가 이름으로 찾는 테이블(`user_states`, `keyword_states`, `data_sources`, `settings`, `report_types`, `sanction_types`, `countries`, `levels`)은 없는 행만 채운다(고유 이름·번호 기준, 기존 값은 덮어쓰지 않는다). 코드를 고쳐 새 설정이나 상태가 늘어도 다음 실행에 채워진다.
- 영어 값(`name_en` 등)은 `seed/EnglishSeed.java`에 있다(칭호 51개, 가이드 카테고리 7개와 글 38개, 효과 21개와 효과 종류 2개). `EnglishBackfill`이 시작할 때마다 영어 칸이 NULL인 행만 채우고(칭호는 한국어 이름, 효과는 코드, 카테고리는 코드, 글은 (카테고리 코드, 한국어 제목)으로 짝지음) 이미 있는 값은 덮어쓰지 않는다. 알려진 한계: 관리자가 영어 칸을 비우면 다음 재시작에 다시 채워진다.
- 운영에서 지우거나 고치는 데이터(`titles`, `guide_categories`, `blocked_nicknames`, `blocked_terms`)는 테이블이 비어 있을 때만 넣는다. 관리자가 일부러 지운 행이 다시 생기지 않게 하려는 것이다.
- ID 순서가 번역 키가 되는 테이블(`titles`, `keyword_states`, `user_states`)은 시드 순서를 고정해서 `reset`을 해도 같은 ID가 나오게 한다.

| 테이블 | 넣는 값 |
|---|---|
| `countries` | KR·GLOBAL(오픈), JP·IN·CN(닫힘), 시간대·기본 언어·방 상한. 이미 있는 DB는 기본값이 바뀌지 않으므로 GLOBAL을 열려면 `UPDATE countries SET enabled = true WHERE code = 'GLOBAL';`을 직접 실행한다 |
| `settings` | 13.3의 기본값과 허용 범위 |
| `data_sources` | google 1, admin 1, internal 1 (10.3) |
| `levels` | 1~100레벨 필요 누적 EXP (4.9의 곡선으로 계산) |
| `user_states`, `keyword_states` | 정상 / 탈퇴, 활성 / 비활성 / 금지 |
| `titles` | 4.10의 51개를 번호 순서대로(테이블이 비어 있을 때만). 표시 순서는 종류별(`sort_order`). 영어 칸은 위 `EnglishBackfill`이 채운다 |
| `effect_types`, `effects` | 효과 종류 TRAIL(이동 효과)·BORDER(테두리 효과), 이동 효과 12개(4.23)와 테두리 효과 5개(4.9), 모두 FREE. 코드에 있는데 DB에 없는 것만 넣는다 |
| `report_types`, `sanction_types` | 키워드 / 댓글 / 아이콘 이미지 / 플레이어 / 채팅, 계정 금지 / 이미지 업로드 정지 / 댓글 작성 정지 (13.5) |
| `guide_categories`, `guides` | 4.17의 초기 카테고리와 놀이 방법 글(각 테이블이 비어 있을 때만). 시드 글을 고쳐도 이미 들어간 DB는 바꾸지 않는다(관리자 화면에서 고친다) |
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
- 주요 의존성: spring-boot-starter-webmvc, starter-websocket, starter-data-jpa, starter-security, starter-validation, starter-flyway와 flyway-mysql(운영용, 개발·테스트에서는 끈다), MySQL JDBC, ShedLock, Caffeine, Google API Client, ONNX Runtime, Testcontainers(MySQL).
- `spring.threads.virtual.enabled=true`(게임 틱 스레드는 플랫폼 스레드).
- `./gradlew bootJar` → Docker 이미지. 프론트엔드는 따로 빌드해 Nginx로 제공한다.
- 로컬 개발: 브라우저는 http://localhost:8080 하나로 접속한다. 프론트 개발 서버(Vite)가 8080에서 화면을 주고 `/api`·`/ws`를 백엔드(8081)로 넘긴다(운영의 Nginx 역할). 로컬 DB는 같은 PC의 MariaDB(DB `trendist`)이고, DB 계정은 git에 올리지 않는 `src/main/resources/application.env`에 둔다.
- 설정은 `application.yml`(공통), `application-dev.yml`(로컬, 기본 프로필), `application-prod.yml`(운영)로 나누고, DB 연결은 환경 변수(`DEV_MYSQL_URL`·`PROD_MYSQL_URL`·`MYSQL_USERNAME`·`MYSQL_PASSWORD`, 운영 포트 `PROD_SERVER_PORT`)로 받는다. 스키마는 Flyway가 바꾸므로 로컬은 `ddl-auto=validate`, 운영은 `ddl-auto=none`이다(11.3, 16.1).
- 백엔드 배포: `main`에 push하면 GitHub Actions가 테스트·`bootJar` 뒤 Docker 이미지(`dlrudgjs104/trendist-back:latest`)를 Docker Hub에 올리고, 서버에서 기존 컨테이너를 내린 뒤 새로 띄운다(단일 컨테이너 중단 배포). 운영 환경 변수는 GitHub 시크릿 `ENV_FILE`을 배포 때 서버 `/home/ubuntu/trendist/.env`로 보내고(WebSocket 허용 주소 `PROD_ALLOWED_ORIGINS`, 기본 `https://trendist.kr`), 실행 설정(포트 4001, 네트워크)은 서버의 docker compose 파일이 맡고, nginx는 `trendist-back:4001`로 넘긴다.
- 카카오톡 공유(4.24): 카카오 앱의 JavaScript 키(공개 키, 브라우저에 그대로 실린다)는 `trendist-front/src/kakaoShare.ts`에 기본값으로 둔다. 다른 키를 쓰려면 빌드 환경 변수 `VITE_KAKAO_JS_KEY`로 덮어쓴다(로컬은 git 제외 파일 `.env.local`). GitHub 저장소 변수는 쓰지 않는다. 카카오 개발자 콘솔(developers.kakao.com, 2025-12 개편 메뉴)에서 사이트 도메인(운영 주소, 로컬은 `http://localhost:8080`)을 두 곳에 등록해야 한다: [앱 → 플랫폼 키 → JavaScript 키 → JavaScript SDK 도메인](SDK 호출 허용)과 [앱 → 제품 링크 관리 → 웹 도메인](공유 메시지의 링크·스크랩 주소). SDK는 `t1.kakaocdn.net`의 카카오 JavaScript SDK 2.7.2를 공유 카드를 열 때만 불러온다.
- 검색엔진 최적화: 메인 `index.html`의 description·canonical·`og:`·twitter 메타(이미지는 절대 주소), `public/robots.txt`(`/admin/`·`/api/` 제외, `/share/`는 미리보기 수집기를 위해 막지 않음), `public/sitemap.xml`(메인 주소), `<noscript>` 소개 본문. 관리자 화면 `admin/index.html`은 `noindex, nofollow`. 공유 카드 페이지는 카드별 `og:` 태그와 `noindex`를 백엔드가 만든다(4.24). 서버 렌더링은 쓰지 않는다(로그인 없는 캔버스 게임이라 검색 노출 대상이 메인 화면뿐).
- 공개 키워드 페이지·사이트맵(4.28): `/kr/keywords/**`, `/global/keywords/**`, `/sitemap.xml`은 백엔드가 준다. **운영 nginx에 `location ~ ^/(kr|global)/keywords/ { proxy_pass http://trendist-back:4001; }`와 `location = /sitemap.xml { proxy_pass http://trendist-back:4001; }`를 `/share/`와 같은 헤더(Host·X-Forwarded)로 추가해야 한다.** `location =`가 정적 파일(`root`)보다 먼저 맞으므로 남은 정적 `sitemap.xml`보다 우선하지만, 혼란을 막으려고 프론트의 `public/sitemap.xml`은 지웠다. 로컬은 Vite가 같은 주소를 백엔드로 넘긴다(`vite.config.ts`). 사이트 주소는 `trendist.share.base-url`을 쓴다. 이미 제출한 `https://trendist.kr/sitemap.xml`은 그대로 쓰면 되고, 새로 제출할 주소는 없다.
- DB 마이그레이션(Flyway, 11.3): 의존성 `spring-boot-starter-flyway`·`flyway-mysql`. 운영 배포 전에 **DB를 백업**한다(처음 Flyway가 켜지면 `flyway_schema_history`가 새로 생기고 V2가 운영 DB에 적용된다). 마이그레이션이 실패하면 애플리케이션이 시작하지 않는다(단일 컨테이너 중단 배포라 이전 컨테이너가 이미 내려가 있다).
- 접속 분석: 메인 `index.html`의 `<head>` 바로 다음에 Google 태그(GTM-WRFP5PLN)를 한 번 둔다. 백엔드가 그리는 공개 페이지(키워드 페이지 `/kr/keywords/…`·`/global/keywords/…`, 공유 카드 페이지 `/share/{코드}`)에는 표준 GTM 코드(`<head>` 스크립트와 `<body>` `<noscript>`, `AnalyticsTag`)를 같은 컨테이너로 넣어 검색에서 들어온 방문도 한곳에서 본다(2026-11-06). 관리자 화면(`admin/`)에는 넣지 않는다(운영자 접속이 수치에 섞이지 않게). 공유 유입은 자체 공유 통계로도 본다(13.8).
- 자랑하기 카드 공유 주소(4.24): `/share/**`는 백엔드가 준다. 운영 nginx에 `location /share/ { proxy_pass http://trendist-back:4001; }`(Host·X-Forwarded 헤더는 `/api`와 같게)를 둬야 하고, 로컬은 Vite가 `/share/`를 백엔드로 넘긴다. OG 태그·공유 주소에 쓰는 사이트 주소는 설정 `trendist.share.base-url`(운영 환경 변수 `PROD_SITE_URL`, 기본 `https://trendist.kr`, 로컬 `http://localhost:8080`)이다. 요청의 Host 헤더로 만들지 않는다(헤더 위조로 다른 주소가 OG에 들어가지 않게). 조회 수의 방문자 구분에 실제 접속 IP가 필요해서 운영은 `server.forward-headers-strategy: native`로 nginx의 `X-Forwarded-For`를 읽는다(nginx가 이 헤더를 넣어야 한다).
- 저장소: 프론트엔드와 백엔드를 GitHub 저장소 두 개로 따로 관리한다(`Trendist-Project/trendist-front`, `Trendist-Project/trendist-back`).
- 기획·기술·운영·SQL 문서는 백엔드 저장소의 `docs/`에서만 관리한다.
