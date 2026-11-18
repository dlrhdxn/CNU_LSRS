# LSRS — 도서관 좌석 예약 시스템

소프트웨어공학 01분반 03조 텀 프로젝트 (주제 A: Library Seat Reservation System)

학생은 좌석 조회 → 예약 → QR 체크인까지 한 흐름으로 이용하고, 15분 내 미체크인 예약은 자동으로 노쇼 처리되며, 관리자는 좌석·이용 현황과 정책을 관리한다.

## 기술 스택 (시스템 구조 설계서 5장)

| 항목 | 내용 |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot 3.5.16 (Web, Security, Data JPA, Validation, Mail) |
| Frontend | Thymeleaf + JavaScript(fetch) — 화면은 기능 확인용으로 단순하게 |
| QR | ZXing — 체크인 payload PNG 생성 |
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
| A-04 | 20230004 | 1분 뒤 ~ 2시간 뒤 | RESERVED | 예약 시작 전후 QR 체크인 테스트 |
| A-03 | 20230002 | 30분 전 ~ 1시간 뒤 | CHECKED_IN | 좌석 '사용 중' 표시 (US-03) |

### 로그인·회원가입

- 브라우저: `/login`에서 학번/ID와 비밀번호로 로그인한다. Access/Refresh JWT는 HttpOnly 쿠키로 전달된다.
- 회원가입은 `/signup`에서 신청하고 `/verify-email`에서 6자리 코드를 입력한다. 이메일 SMTP 설정이 없으면 개발 환경에서 코드가 애플리케이션 로그에 기록된다.
- API: `POST /api/auth/login`으로 로그인하고, 이후 쿠키를 사용하거나 `Authorization: Bearer <access-token>` 헤더를 보낼 수 있다. `POST /api/auth/refresh`는 Refresh 쿠키로 토큰을 갱신한다.
- 로그아웃은 공통 네비게이션의 버튼으로 수행하며 Access/Refresh 쿠키를 삭제한다.
- 테스트 호환용: 기존 개발 도구에서는 HTTP Basic 인증을 계속 사용할 수 있다.

## 프로젝트 구조 (설계서 2장 논리 아키텍처)

```
src/main/java/com/cnu/lsrs
├── controller/   Presentation: 요청·응답, @Valid 검증, DTO 매핑
├── service/      Application: 비즈니스 로직, @Transactional 경계
├── repository/   Persistence: Spring Data JPA
├── domain/       Domain: 엔티티(User, Seat, Reservation, NoShowRecord, ReservationPolicy)와 Enum
├── security/     LoginUser(로그인 사용자), UserDetailsService, JwtProvider, JwtAuthFilter
├── config/       SecurityConfig, Clock, 정책 설정(LsrsProperties), 초기 데이터
└── common/       예외 처리 (BusinessException, GlobalExceptionHandler)
src/main/resources
├── application.yml          정책 값(15분·3회·3일·7일/4시간 등) — NFR 4.1
├── templates/               Thymeleaf 화면 (fragments/layout.html 공통)
│   ├── login.html, signup.html, verify-email.html
│   ├── admin/dashboard.html
│   └── reservations/me.html, reservations/qr.html
└── static/                  CSS 및 API·좌석·내 예약 JavaScript
```

모든 Thymeleaf 페이지 경로는 `PageController`에서 처리하고, REST API는 기능별 Controller에서 처리한다. 내 예약 화면(`/reservations/me`)은 로그인한 사용자의 예약 이력, 진행 중 예약의 남은 시간, QR 발급이 가능한 예약의 QR 링크를 제공한다.

### 대표 화면 구조

화면은 사용자 흐름별 페이지로 구성한다. 아래는 대표 사용자 화면이며, 체크인 단말 화면은 포함하지 않는다.

| 화면 | 경로 | 주요 기능 | 접근 역할 |
|---|---|---|---|
| 로그인 | `/login` | 학생·관리자 인증 | 비로그인 |
| 회원가입 | `/signup` | 학생 가입 및 이메일 인증 | 비로그인 |
| 홈 | `/` | 서비스 안내 및 로그인·회원가입 이동 | 전체 |
| 좌석 조회·예약 | `/seats` | 좌석 상태 조회와 예약·취소 | STUDENT, ADMIN |
| 예약 기록 | `/reservations/me` | 내 예약·이력과 이용 종료까지 남은 시간 확인; 예약 시작 후 체크인 15분 동안 QR 버튼·마감 카운트다운 표시 | STUDENT, ADMIN |
| 예약 QR | `/reservations/{id}/qr` | 체크인 가능 시간(예약 시작~15분) 내 본인 예약 QR 표시 | 예약 소유 STUDENT, ADMIN |
| 관리자 대시보드 | `/admin` | 좌석·이용 현황 및 운영 관리 | ADMIN |

학생과 관리자는 공통 네비게이션에서 좌석 조회와 내 예약에 접근할 수 있다. 관리자에게는 관리자 대시보드 링크도 표시한다. QR은 내 예약에서 해당 예약을 선택해 별도 화면으로 연다. `/api/...` 경로는 화면과 구분되는 REST API다.

### E1 인증 API

| Method | Endpoint | 설명 |
|---|---|---|
| POST | `/api/auth/register` | 학생 회원가입, PENDING 계정 생성 및 6자리 이메일 인증 코드 발송 |
| POST | `/api/auth/email-verification` | 학번 또는 이메일과 인증 코드로 계정 활성화 |
| POST | `/api/auth/login` | 학번/관리자 ID와 비밀번호 검증, Access·Refresh JWT를 HttpOnly 쿠키로 발급 |
| POST | `/api/auth/refresh` | Refresh 쿠키 검증 후 JWT 쿠키 갱신 |
| POST | `/api/auth/logout` | Access·Refresh 쿠키 삭제 후 로그인 화면으로 이동 |

비밀번호는 8자 이상이며 영문·숫자·특수문자를 포함해야 한다. JWT 기본 만료 시간은 Access 1시간, Refresh 7일이다. 이메일 인증 전인 PENDING 계정은 로그인할 수 없다. 예약 QR은 예약 시작 시각부터 설정된 체크인 허용 시간(기본 15분) 안에서만 열 수 있으며, 그 전·후의 직접 URL 접근은 거부된다.

### E3 QR 및 E4 관리자 API

| Method | Endpoint | 권한 | 설명 |
|---|---|---|---|
| POST | `/api/check-in` | KIOSK | `{seatCode, qrToken}` 검증 후 체크인 |
| GET | `/api/admin/seats` | ADMIN | 이용 인원·잔여 좌석·가동률·좌석별 상태; 대시보드는 10초마다 갱신 |
| GET | `/api/admin/users?studentNo=` | ADMIN | 전체 사용자 목록 또는 학번 검색, 학생의 진행 중·예정 예약과 제한 상태 (지난 예약·노쇼 상세 이력 및 비밀번호 해시·인증 코드는 제외) |
| GET | `/api/admin/statistics?startAt=&endAt=` | ADMIN | 지정 기간을 한 시간 단위로 나눠 실제 체크인 사용자 수·가동률과 기간 전체 평균 가동률 조회 |
| PATCH | `/api/admin/{userId}/restriction` | ADMIN | 제한 설정·기간 변경 또는 해제 (해제 시 노쇼 횟수 0으로 초기화) |
| PATCH | `/api/admin/{userId}/no-show-count` | ADMIN | 해당 학생의 누적 노쇼 횟수만 초기화 (이용 제한 상태는 유지) |
| PATCH | `/api/admin/{userId}/role` | ADMIN | 사용자 역할을 `STUDENT`, `ADMIN`, `KIOSK` 중 하나로 변경 |
| POST | `/api/admin/reservations/{id}/cancel` | ADMIN | 활성 예약 강제 취소 및 선택적 노쇼 패널티 |
| GET, PUT | `/api/admin/policies` | ADMIN | 평상시·시험기간 정책 조회, 시험기간 설정 |
| DELETE | `/api/admin/policies/exam` | ADMIN | 설정된 시험기간 정책 전체 해제; 기존 예약은 유지 |

QR은 별도 데이터 API 없이 `/reservations/{id}/qr` 페이지에서 예약 소유자와 상태를 확인한 뒤, 페이지에 PNG를 직접 포함해 표시한다. 예약 토큰이 든 페이지 응답은 캐시하지 않는다. 정책의 시험기간 예약 일수·최대 시간은 `lsrs.policy.exam` 설정값을 사용한다.

## 담당 (에픽 단위)

| 담당 | 에픽 | Sprint 1 | Sprint 2 | 주로 만드는 파일 |
|---|---|---|---|---|
| A | E1 회원·인증 | US-01 회원가입·이메일 인증<br>US-02 역할별 로그인 | — | `AuthController`, `AuthService`, `MailService`, `security/JwtProvider`, `security/JwtAuthFilter`, 인증 화면, `SecurityConfig` |
| B | E2 좌석 조회·예약<br>E3 노쇼 | US-03 좌석 조회<br>US-04 좌석 예약<br>US-05 예약 취소 | US-06 예약 이력<br>US-09 노쇼 자동 처리<br>US-10 반복 노쇼 이용 제한 | `SeatController`, `SeatService`, `ReservationController`, `ReservationService`, `NoShowScheduler`, `NoShowService`, 좌석·내 예약 화면 |
| C | E3 QR 체크인<br>E4 관리자·운영 | US-07 QR 발급·확인<br>US-08 QR 체크인 | US-11 대시보드<br>US-12 이용 기록<br>US-13 제한 해제<br>US-14 시험기간 정책<br>US-15 강제 취소 | `PageController`, `ReservationQrService`, `QrCodeImageGenerator`, `CheckInController`, `CheckInService`, `QrTokenGenerator`, `AdminController`, `AdminService`, `PolicyService`, 관리자 화면 |

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
| `domain/*` 엔티티 | 필드·상태 변경이 필요하면 **단톡에 먼저 공유 → 필요한 사람이 수정해 PR로 리뷰** |
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
