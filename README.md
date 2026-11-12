# LSRS — 도서관 좌석 예약 시스템

소프트웨어공학 01분반 03조 텀 프로젝트 (주제 A: Library Seat Reservation System)

학생은 좌석 조회 → 예약 → QR 체크인까지 한 흐름으로 이용하고, 15분 내 미체크인 예약은 자동으로 노쇼 처리되며, 관리자는 좌석·이용 현황과 정책을 관리한다.

## 기술 스택 (시스템 구조 설계서 5장)

| 항목 | 내용 |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot 3.5.16 (Web, Security, Data JPA, Validation, Mail) |
| Frontend | Thymeleaf + JavaScript(fetch) — 화면은 기능 확인용으로 단순하게 |
| 인증 | JWT (Access 1시간 / Refresh 7일, HttpOnly 쿠키), BCrypt |
| DB | H2 (개발·테스트) → MySQL 8 (시연) |
| Build | Gradle 8.14.7 (Wrapper 포함, 별도 설치 불필요) |
| Test | JUnit 5, Spring Security Test, MockMvc |

## 실행 방법

필요한 것: **JDK 17** 하나. Gradle은 `gradlew`가 자동으로 받는다.

```bash
./gradlew bootRun          # Windows: gradlew.bat bootRun
./gradlew test             # 전체 테스트
```

IntelliJ: `File → Open`으로 이 폴더를 열고 `LsrsApplication` 실행.

실행 후 http://localhost:8080

- 기본 프로필은 `dev` (메모리 H2, 실행할 때마다 초기화 + 초기 데이터 생성)
- H2 콘솔: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:lsrs`, User `sa`, 비밀번호 없음)
- MySQL로 실행: `./gradlew bootRun --args='--spring.profiles.active=mysql'` (DB_URL, DB_USERNAME, DB_PASSWORD 환경변수)

## 테스트 계정 (dev 초기 데이터, 비밀번호 모두 `Test1234!`)

| 로그인 ID | 역할 | 상태 |
|---|---|---|
| 20230001 | STUDENT | ACTIVE |
| 20230002 | STUDENT | ACTIVE |
| 20230003 | STUDENT | PENDING (이메일 인증 전, 인증 코드 `123456`) → 로그인 불가 |
| admin | ADMIN | ACTIVE |
| kiosk1 | KIOSK (체크인 단말) | ACTIVE |

좌석: A-01 ~ A-12 (1층), B-01 ~ B-08 (2층, **B-08은 사용 불가**)

예약 (앱 시작 시각 기준):

| 좌석 | 학생 | 시간 | 상태 | 확인용 |
|---|---|---|---|---|
| A-01 | 20230001 | 5분 전 ~ 2시간 뒤 | RESERVED | QR 체크인 (US-08). 시작 후 15분이 지나면 체크인 불가 → **앱을 재시작하면 다시 생성됨** |
| A-02 | 20230002 | 2시간 뒤 ~ 4시간 뒤 | RESERVED | 취소 (US-05), 좌석 '예약됨' 표시 (US-03) |
| A-03 | 20230002 | 30분 전 ~ 1시간 뒤 | CHECKED_IN | 좌석 '사용 중' 표시 (US-03) |

### [임시] 로그인 방법 — JWT 완성 전까지

지금은 **HTTP Basic 인증**으로 위 계정을 쓸 수 있다.

- Postman: `Authorization → Basic Auth`에 ID/비밀번호 입력
- curl: `curl -u 20230001:Test1234! http://localhost:8080/api/...`
- 브라우저: 로그인이 필요한 화면에 들어가면 로그인 창이 뜬다

E1 담당자가 JWT 로그인을 완성하면 이 방식은 제거된다 (`SecurityConfig` 주석 참고).

## 프로젝트 구조 (설계서 2장 논리 아키텍처)

```
src/main/java/com/cnu/lsrs
├── controller/   Presentation: 요청·응답, @Valid 검증, DTO 매핑
├── service/      Application: 비즈니스 로직, @Transactional 경계
├── repository/   Persistence: Spring Data JPA
├── domain/       Domain: 엔티티(User, Seat, Reservation, NoShowRecord, ReservationPolicy)와 Enum
├── security/     LoginUser(로그인 사용자), UserDetailsService (+ JWT 필터 예정)
├── config/       SecurityConfig, Clock, 정책 설정(LsrsProperties), 초기 데이터
└── common/       예외 처리 (BusinessException, GlobalExceptionHandler)
src/main/resources
├── application.yml          정책 값(15분·3회·3일·7일/4시간 등) — NFR 4.1
├── templates/               Thymeleaf 화면 (fragments/layout.html 공통)
└── static/                  css/common.css, js/api.js(fetch 공통 함수)
```

## 담당 (에픽 단위)

| 담당 | 에픽 | Sprint 1 US | 주로 만드는 파일 |
|---|---|---|---|
| A | E1 회원·인증 | US-01 회원가입·이메일 인증, US-02 역할별 로그인 | `AuthController`, `AuthService`, `MailService`, `security/JwtProvider`, `security/JwtAuthFilter`, 로그인·가입 화면, `SecurityConfig`의 JWT 부분 |
| B | E2 좌석 조회·예약 | US-03 좌석 조회, US-04 좌석 예약, US-05 예약 취소 | `SeatController`, `SeatService`, `ReservationController`, `ReservationService`, 좌석·내 예약 화면 |
| C | E3 QR 체크인·노쇼 | US-07 QR 발급·확인, US-08 QR 체크인 | `QrController`(GET `/api/reservations/{id}/qr`), `CheckInController`, `CheckInService`, `QrTokenGenerator`, 체크인 단말 화면 |

공통 파일(아래 "공유 파일 규칙")은 C가 관리한다.

## 협업 규칙

### Issue → 브랜치 → PR

1. **Issue**: US 하나당 Issue 하나. 본문에 Acceptance Criteria 체크리스트.
2. **브랜치**: `feature/US-04-reservation`처럼 Issue마다 만든다. `main`에 직접 push하지 않는다.
3. **PR**: 본문에 `Closes #이슈번호`. **다른 팀원 1명 이상 리뷰 후** 병합.
4. 작업 시작 전 항상 `git pull origin main`으로 최신 코드를 받는다.

### 공유 파일 규칙 (병합 충돌 방지)

여러 사람이 쓰는 파일은 아래처럼만 다룬다.

| 파일 | 규칙 |
|---|---|
| `domain/*` 엔티티 | 필드·상태 변경이 필요하면 **단톡에 먼저 공유 → 관리 담당(C)이 수정** |
| `repository/*` | 필요한 쿼리 메서드를 **추가만** 한다. 남이 만든 메서드는 고치지 않는다 |
| `SecurityConfig` URL 규칙 | 설계서 API 목록 기준으로 이미 다 적혀 있다. 바꿔야 하면 공유 후 수정 |
| `application.yml`, `build.gradle` | 공유 후 수정 (의존성 추가 등) |
| `templates/fragments/layout.html`, `static/css/common.css`, `static/js/api.js` | 공유 후 수정. 기능별 CSS/JS는 각자 새 파일로 |

### 코드 약속

- **현재 시각**: `LocalDateTime.now()` 대신 주입받은 `Clock`으로 `LocalDateTime.now(clock)`. 테스트에서 시간을 고정할 수 있어야 한다 (NFR 4.2, 15분 체크인 검증).
- **정책 값**: 15분, 3회, 3일, 7일/4시간 등은 코드에 쓰지 말고 `LsrsProperties`(application.yml)에서 읽는다 (NFR 4.1).
- **현재 사용자**: 컨트롤러에서 `@AuthenticationPrincipal LoginUser loginUser` → `loginUser.getUserId()`.
- **업무 오류**: `throw BusinessException.conflict("SEAT_ALREADY_RESERVED", "이미 예약된 좌석입니다.")` → 자동으로 `{code, message}` JSON 응답. code 문자열은 각자 정한다.
- **예약 상태 변경**: `reservation.checkIn(now)`, `cancelByStudent(reason)` 등 엔티티 메서드로만 한다. 허용되지 않은 전이는 엔티티가 막는다.
- **계층**: Controller → Service → Repository (NFR 4.3). Controller에서 Repository를 직접 부르지 않는다.
- **테스트**: 자기 US의 Acceptance Criteria는 테스트로 확인한다. 스프린트 로그의 완료 기준이 AC 충족이다.

### 비밀 값 금지 (Public 저장소)

메일 비밀번호, JWT 비밀키, DB 비밀번호는 **절대 커밋하지 않는다.**
`src/main/resources/application-local.yml.example`을 `application-local.yml`로 복사해 값을 넣으면 자동으로 읽히고, 이 파일은 `.gitignore`로 제외된다.

## AI 사용 기록

AI가 만든 코드는 PR 템플릿의 "AI 사용" 칸에 적는다. 특히 **AI가 틀렸던 부분과 어떻게 발견·수정했는지**는 스프린트 로그의 "AI 오류 대응 사례"로 쓰이므로 그때그때 남긴다.

- 공통 뼈대: Claude Code로 생성, 테스트 31개로 검증 후 팀 리뷰
