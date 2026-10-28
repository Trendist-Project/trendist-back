# trendist-back

Trendist(트렌디스트) 백엔드. REST API, WebSocket 게임 서버, 배치를 한 Spring Boot 애플리케이션으로 돌린다.
기획과 설계 기준은 [`docs/`](docs/README.md)의 기획·기술·운영·SQL 문서이고, 코드 주석의 `(4.8)` 같은 번호는 그 문서들의 절 번호다.

## 개발 환경

| 항목 | 버전 |
|---|---|
| Java | 25 (Gradle 툴체인과 Gradle 실행 JVM 모두 25) |
| Spring Boot | 4.1.2 |
| Gradle | 9.8.2 (Wrapper) |
| DB | 로컬은 H2 메모리(MySQL 모드), `mysql` 프로필은 MySQL 8 |

- 시스템 `JAVA_HOME`이 17 미만이면 프로젝트 폴더에 `.java-home` 파일을 만들고 JDK 25 경로를 한 줄 적는다(git 제외 파일). `gradlew`가 이 경로로 Gradle을 띄운다.
- IntelliJ는 **Settings → Build Tools → Gradle → Gradle JVM**을 `temurin-25 (WSL)`로 둔다. `gradle/gradle-daemon-jvm.properties`(데몬 JVM 지정)는 WSL에서 IntelliJ가 JDK를 찾지 못해 동기화가 실패하므로 쓰지 않는다.

```bash
./gradlew test                                   # 테스트
./gradlew bootRun                                # 로컬 실행(H2, 포트 8081)
./gradlew bootRun --args='--spring.profiles.active=mysql'
./gradlew bootRun --args='--spring.profiles.active=local,reset'   # 로컬 DB 테이블을 모두 지우고 다시 만든다
```

### 로컬에서 띄워 보기
브라우저는 항상 **http://localhost:8080** 하나로 접속한다. 프론트 개발 서버(Vite)가 8080에서 화면을 주고, `/api`와 `/ws`는 백엔드(8081)로 넘긴다. 운영의 Nginx와 같은 구조다.

| 대상 | 포트 | 실행 |
|---|---|---|
| 백엔드(Spring Boot) | 8081 | `trendist-back`에서 `./gradlew bootRun` |
| 프론트(Vite) | 8080 | `trendist-front`에서 `npm run dev` |

- 백엔드 코드를 고치면 백엔드를 다시 띄운다. 프론트는 저장하면 바로 반영된다.
- 로컬 DB는 H2 메모리라 백엔드를 다시 띄우면 데이터가 비워진다(게스트도 새로 시작).
- 백엔드 포트는 환경 변수 `SERVER_PORT`로 바꿀 수 있다. 바꾸면 프론트 `vite.config.ts`의 `BACKEND`도 맞춘다.

## 작업 방식

- 혼자 개발하므로 `main` 브랜치에서 바로 작업한다. 기능별 브랜치는 만들지 않는다.
- 성격이 다른 변경(구조 변경, 기능 추가, 버그 수정)은 커밋을 나눈다.
- 기능을 바꾸면 같은 작업에서 `docs/` 문서의 해당 절도 고치고, `docs/README.md` 변경 이력에 남긴다.
- 커밋 메시지는 `type: 작업한 내용` 형식으로 쓴다. 내용은 한국어로 쓴다.

| type | 쓰는 때 |
|---|---|
| `feat` | 기능 추가·변경 |
| `fix` | 버그 수정 |
| `refactor` | 동작은 그대로 두고 구조만 바꿈 |
| `docs` | 문서(README, 기획서) |
| `build` | 빌드 설정, 의존성, 개발 환경 |
| `test` | 테스트만 추가·수정 |
| `chore` | 그 밖의 잡일 |

예: `feat: 미니맵 추가 (M 키로 켜고 끄기)`

## 코딩 컨벤션

새 규칙은 아래에 번호를 이어서 하나씩 추가한다. 규칙을 바꾸면 기존 코드도 같이 맞춘다.

### 1. 패키지 구조
도메인별로 나누고, 도메인 안에서 계층별로 나눈다.

```
com.trendist.<도메인>
├─ controller   REST 컨트롤러(게임은 WebSocket 핸들러)
├─ service      서비스 인터페이스 + 구현(XxxServiceImpl)
├─ repository   Spring Data JPA 리포지토리
├─ entity       JPA 엔티티
├─ dto          요청·응답 객체
└─ support      그 도메인 안에서만 쓰는 도우미(검증기, 계산식, 카운터 등)
```

- 도메인: `auth`, `user`, `country`, `player`, `ranking`, `keyword`, `title`, `setting`, `game`
- 도메인 공통은 `common`(예외, 코드 테이블, `BaseTimeEntity`), 스프링 설정은 `config`, 초기 데이터는 `seed`
- 필요한 계층만 만든다(예: `title`은 아직 `entity`, `repository`뿐). 도메인 고유의 하위 패키지는 이름으로 역할을 드러낸다(`keyword/source`, `keyword/job`, `game/room`, `game/event`).

### 2. 계층 책임
- **controller**: 요청 값 받기, 응답 만들기, 쿠키·헤더 같은 HTTP 처리만 한다. 리포지토리를 직접 부르지 않고 서비스만 부른다.
- **service**: 업무 규칙과 트랜잭션. `@Transactional`은 구현 클래스의 메서드에 붙인다.
- **repository**: 조회와 저장만. 비관적 잠금 조회는 `findByIdForUpdate`처럼 이름 끝에 `ForUpdate`를 붙인다.
- 다른 도메인의 데이터가 필요하면 그 도메인의 서비스(또는 리포지토리)를 주입받는다. 서비스끼리 순환 의존이 생기면 역할을 새 서비스로 뺀다(예: 랭킹 → `RankingService`).

### 3. 서비스는 인터페이스 + 구현
- `XxxService`(인터페이스)와 `XxxServiceImpl`(`@Service`)을 같은 `service` 패키지에 둔다.
- 다른 클래스는 항상 인터페이스 타입으로 주입받는다.
- 설명 주석(Javadoc)은 인터페이스에 쓰고, 구현 메서드에는 `@Override`를 붙인다.
- 인터페이스에만 필요한 작은 타입(결과 묶음 record 등)은 인터페이스 안에 중첩해 둔다(예: `PlayerService.JoinInfo`).

### 4. Lombok
- **엔티티**: `@Getter` + `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
  - 클래스 단위 `@Setter`, `@Data`, `@ToString`, `@EqualsAndHashCode`는 쓰지 않는다. 지연 로딩·순환 참조 문제와, JPA 변경 감지 때문에 어디서 부른 setter든 그대로 DB에 저장되는 문제 때문이다.
  - 다른 값과 엮인 규칙이 없는 단순한 필드(예: 사용자 설정값)에만 필드 단위로 `@Setter`를 붙인다.
  - 점수·인원·상태 전이처럼 규칙이 있는 값은 의도가 드러나는 메서드로만 바꾼다(`activate()`, `incHolder()`, `renew()` 등). 예: `holderCount`가 바뀌면 `trendValue`도 다시 계산해야 하고, 0 아래로 내려가면 안 된다.
  - 레이어 밖(응답, 다른 도메인에 넘기는 값)으로는 엔티티가 아니라 DTO를 내보낸다(5번).
- **스프링 빈(서비스, 컨트롤러, 컴포넌트)**: `@RequiredArgsConstructor` + `private final` 필드로 생성자 주입한다.
  - 생성자 안에서 계산·검증·캐시 생성이 필요하면 생성자를 직접 쓴다(예: `JwtServiceImpl`, `SettingServiceImpl`, `RoomManagerImpl`).

### 5. DTO
- 요청·응답은 `record`로 만든다. 엔티티를 응답으로 그대로 내보내지 않는다.
- 한 도메인의 DTO가 여러 개면 `XxxDtos` 클래스 안에 중첩 record로 묶는다(예: `KeywordDtos.SearchResult`). 하나뿐이면 단독 파일로 둔다(예: `RankingEntry`).

### 6. 오류
- 클라이언트에 보내는 오류는 `ApiException`으로 던진다. 코드(`KEYWORD_LENGTH` 같은 대문자 스네이크)와 한국어 메시지를 함께 담는다.
- 클라이언트는 메시지가 아니라 코드로 판단한다(다국어 대비, 기획서 8절).

### 7. 설정값과 시간
- 밸런스 숫자(점수식 계수, 보호 시간, 속도 등)는 코드에 직접 쓰지 않고 `settings` 테이블에서 읽는다. 새 값은 `SettingKeys`에 이름·기본값·허용 범위를 추가한다(기획서 13.3).
- 현재 시각은 주입받은 `Clock`으로 구하고, 시각은 `Instant`(UTC)로 다룬다.

### 8. 게임 서버
- `Room`의 상태는 그 방의 틱 스레드에서만 읽고 쓴다. 다른 스레드는 `room.submit(...)`으로 작업을 넣는다.
- DB·외부 호출은 틱 스레드에서 하지 않고 `RoomManager.submitWork(...)`로 넘긴다.
- 서비스에서 방으로 알릴 일은 `GameEvents`의 이벤트를 발행한다(트랜잭션 커밋 뒤 전달).

### 9. 주석
- 주석은 한국어로 쓰고, 근거가 기획서에 있으면 절 번호를 붙인다(예: `// 현재 유행도가 낮은 쪽이 키워드 하나를 잃는다(4.3).`).
- 무엇을 하는지보다 왜 그렇게 하는지를 쓴다.
